package bb.spring.boot.thrift.server;

import bb.spring.boot.thrift.server.annotation.ThriftController;
import bb.spring.boot.thrift.server.interceptor.MetricsThriftMethodInterceptor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TServlet;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.embedded.RegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ClassUtils;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import java.lang.reflect.Constructor;

/**
 * Created by bob on 16/1/11.
 */
@Configuration
@ConditionalOnClass(value = {ThriftController.class})
@ConditionalOnWebApplication
public class ThriftServerAutoConfiguration {

    public interface ThriftConfigurer {
        void configureProxyFactory(ProxyFactory proxyFactory);
    }

    @Bean
    @ConditionalOnMissingBean(ThriftConfigurer.class)
    ThriftConfigurer thriftConfigurer() {
        return new DefaultThriftConfigurer();
    }

    public static class DefaultThriftConfigurer implements ThriftConfigurer {

        @Autowired(required = false)
        private GaugeService gaugeService;

        @Autowired(required = false)
        private CounterService counterService;

        public void configureProxyFactory(ProxyFactory proxyFactory) {
            proxyFactory.setOptimize(true);

            if (gaugeService != null) {
                proxyFactory.addAdvice(new MetricsThriftMethodInterceptor(gaugeService, counterService));
            }
        }
    }

    @Bean
    @ConditionalOnMissingBean(TProtocolFactory.class)
    TProtocolFactory thriftProtocolFactory() {
        return new TBinaryProtocol.Factory();
    }

    @Configuration
    public static class Registrar extends RegistrationBean implements ApplicationContextAware {

        private ApplicationContext applicationContext;

        @Autowired
        private TProtocolFactory protocolFactory;
        @Autowired
        private ThriftConfigurer thriftConfigurer;

        @Override
        public void onStartup(ServletContext servletContext) throws ServletException {
            String[] beans = applicationContext.getBeanNamesForAnnotation(ThriftController.class);
            for (String beanName : beans) {
                ThriftController annotation = applicationContext.findAnnotationOnBean(beanName, ThriftController.class);
                try {
                    register(servletContext, annotation.value(), protocolFactory.getClass(), applicationContext.getBean(beanName));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InstantiationException e) {
                    e.printStackTrace();
                }
            }
        }

        protected void register(ServletContext servletContext, String[] urls, Class<? extends TProtocolFactory> factory, Object handler)
                throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InstantiationException {

            Class ifaceClass = null;
            Class<TProcessor> processorClass = null;
            Class serviceClass = null;

            Class<?>[] handlerInterfaces = ClassUtils.getAllInterfaces(handler);
            for (Class<?> handlerInterfaceClass : handlerInterfaces) {
                if (!handlerInterfaceClass.getName().endsWith("$Iface")) {
                    continue;
                }

                serviceClass = handlerInterfaceClass.getDeclaringClass();
                if (serviceClass == null) {
                    continue;
                }

                for (Class<?> innerClass : serviceClass.getDeclaredClasses()) {
                    if (!innerClass.getName().endsWith("$Processor")) {
                        continue;
                    }

                    if (!TProcessor.class.isAssignableFrom(innerClass)) {
                        continue;
                    }

                    if (ifaceClass != null) {
                        throw new IllegalStateException("Multiple Thrift Ifaces defined on handler");
                    }

                    ifaceClass = handlerInterfaceClass;
                    processorClass = (Class<TProcessor>) innerClass;
                    break;
                }
            }

            if (ifaceClass == null) {
                throw new IllegalStateException("No Thrift Ifaces found on handler");
            }

            handler = wrapHandler(ifaceClass, handler);
            Constructor<TProcessor> processorConstructor = processorClass.getConstructor(ifaceClass);
            TProcessor processor = BeanUtils.instantiateClass(processorConstructor, handler);

            TServlet servlet;
            if (TProtocolFactory.class.equals(factory)) {
                servlet = getServlet(processor, protocolFactory);
            } else {
                servlet = getServlet(processor, factory.newInstance());
            }

            String servletBeanName = handler.getClass().getSimpleName() + "Servlet";
            ServletRegistration.Dynamic registration = servletContext.addServlet(servletBeanName, servlet);

            if (urls != null && urls.length > 0) {
                registration.addMapping(urls);
            } else {
                registration.addMapping("/" + serviceClass.getSimpleName());
            }
        }

        protected TServlet getServlet(TProcessor processor, TProtocolFactory protocolFactory) {
            return new TServlet(processor, protocolFactory);
        }

        protected <T> T wrapHandler(Class<T> interfaceClass, T handler) {
            ProxyFactory proxyFactory = new ProxyFactory(interfaceClass, new SingletonTargetSource(handler));
            thriftConfigurer.configureProxyFactory(proxyFactory);
            proxyFactory.setFrozen(true);
            return (T) proxyFactory.getProxy();
        }

        @Override
        public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
            this.applicationContext = applicationContext;
        }
    }

}