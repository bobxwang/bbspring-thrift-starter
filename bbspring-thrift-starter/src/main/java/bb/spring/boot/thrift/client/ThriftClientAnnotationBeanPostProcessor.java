package bb.spring.boot.thrift.client;

import bb.spring.boot.thrift.client.annotation.ThriftClient;
import bb.spring.boot.thrift.client.pool.ThriftKey;
import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import org.aopalliance.intercept.MethodInterceptor;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.thrift.TException;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocol;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by bob on 17/1/11.
 */
public class ThriftClientAnnotationBeanPostProcessor implements BeanPostProcessor {

    @Autowired
    private DefaultListableBeanFactory beanFactory;
    @Autowired
    private KeyedObjectPool<ThriftKey, TServiceClient> thriftClientsPool;

    private Map<String, List<Class>> beansToProcess = new HashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object o, String s) throws BeansException {
        Class clazz = o.getClass();
        do {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(ThriftClient.class)) {
                    if (!beansToProcess.containsKey(s)) {
                        beansToProcess.put(s, new ArrayList<>());
                    }
                    beansToProcess.get(s).add(clazz);
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        return o;
    }

    @Override
    public Object postProcessAfterInitialization(Object o, String s) throws BeansException {
        if (beansToProcess.containsKey(s)) {
            Object target = getTargetBean(o);
            for (Class clazz : beansToProcess.get(s)) {
                for (Field field : clazz.getDeclaredFields()) {
                    ThriftClient annotation = AnnotationUtils.getAnnotation(field, ThriftClient.class);
                    if (null != annotation) {

                        Class<?> filedClass = field.getType();
                        if (!filedClass.getSuperclass().getSimpleName().equalsIgnoreCase(TServiceClient.class.getSimpleName())) {
                            throw new RuntimeException("ThriftClient Annotation Should only using in subclass of TServiceClient");
                        }

                        if (beanFactory.containsBean(field.getName())) {
                            ReflectionUtils.makeAccessible(field);
                            ReflectionUtils.setField(field, target, beanFactory.getBean(field.getName()));
                        } else {
                            ProxyFactory proxyFactory = getProxyFactoryForThriftClient(target, field);
                            addPoolAdvice(proxyFactory, annotation);
                            proxyFactory.setFrozen(true);
                            proxyFactory.setProxyTargetClass(true);
                            ReflectionUtils.makeAccessible(field);
                            ReflectionUtils.setField(field, target, proxyFactory.getProxy());
                        }
                    }
                }
            }
        }
        return o;
    }

    private Object getTargetBean(Object bean) {
        Object target = bean;
        while (AopUtils.isAopProxy(target)) {
            try {
                target = ((Advised) target).getTargetSource().getTarget();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return target;
    }

    private ProxyFactory getProxyFactoryForThriftClient(Object bean, Field field) {
        ProxyFactory proxyFactory;
        try {
            proxyFactory = new ProxyFactory(
                    BeanUtils.instantiateClass(
                            field.getType().getConstructor(TProtocol.class),
                            (TProtocol) null
                    )
            );
        } catch (NoSuchMethodException e) {
            throw new InvalidPropertyException(bean.getClass(), field.getName(), e.getMessage());
        }
        return proxyFactory;
    }

    @SuppressWarnings("unchecked")
    private void addPoolAdvice(ProxyFactory proxyFactory, ThriftClient annotation) {

        proxyFactory.addAdvice((MethodInterceptor) methodInvocation -> {
            Object[] args = methodInvocation.getArguments();
            Class<? extends TServiceClient> declaringClass = (Class<? extends TServiceClient>) methodInvocation.getMethod().getDeclaringClass();
            ThriftKey key = new ThriftKey(declaringClass);
            key.setServiceName(annotation.serviceId());
            key.setPath(annotation.path());

            TServiceClient thriftClient = null;
            try {
                thriftClient = thriftClientsPool.borrowObject(key);
                HystrixServiceClient hystrixServiceClient = new HystrixServiceClient(thriftClient, methodInvocation.getMethod(), args);
                return hystrixServiceClient.execute();
            } catch (UndeclaredThrowableException e) {
                if (TException.class.isAssignableFrom(e.getUndeclaredThrowable().getClass()))
                    throw (TException) e.getUndeclaredThrowable();
                throw e;
            } finally {
                if (null != thriftClient) {
                    thriftClientsPool.returnObject(key, thriftClient);
                }
            }
        });
    }

    private class HystrixServiceClient extends HystrixCommand<Object> {

        private final TServiceClient serviceClient;
        private final Method method;
        private final Object[] args;

        private HystrixServiceClient(final TServiceClient serviceClient, final Method method, final Object[] args) {
            super(HystrixCommand.Setter
                    .withGroupKey(HystrixCommandGroupKey.Factory.asKey(serviceClient.getClass().getName()))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(serviceClient.getClass().getName() + "." + method.getName()))
            );
            this.serviceClient = serviceClient;
            this.method = method;
            this.args = args;
        }

        @Override
        protected Object run() throws Exception {
            return ReflectionUtils.invokeMethod(method, serviceClient, args);
        }
    }
}