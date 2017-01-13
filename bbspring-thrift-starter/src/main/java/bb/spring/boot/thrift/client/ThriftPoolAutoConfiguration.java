package bb.spring.boot.thrift.client;

import bb.spring.boot.thrift.client.pool.ThriftKey;
import bb.spring.boot.thrift.client.pool.ThriftPool;
import bb.spring.boot.thrift.client.pool.ThriftPooledObjectFactory;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.PropertyResolver;

/**
 * Created by bob on 17/1/11.
 */
@Configuration
@AutoConfigureAfter({TraceAutoConfiguration.class, ThriftClientAutoConfiguration.class})
@ConditionalOnBean(Tracer.class)
public class ThriftPoolAutoConfiguration {

    @Autowired
    private TProtocolFactory protocolFactory;
    @Autowired
    private LoadBalancerClient loadBalancerClient;
    @Autowired
    private PropertyResolver propertyResolver;
    @Value("${thrift.client.max.poolobject:10}")
    private int maxThreads;
    @Autowired
    private Tracer tracer;
    @Autowired
    private SpanInjector<TTransport> thriftTransportSpanInjector;

    @Bean
    public KeyedObjectPool<ThriftKey, TServiceClient> thriftClientsPool() {
        GenericKeyedObjectPoolConfig poolConfig = new GenericKeyedObjectPoolConfig();
        poolConfig.setMaxTotal(maxThreads);
        poolConfig.setMaxIdlePerKey(maxThreads);
        poolConfig.setMaxTotalPerKey(maxThreads);
        poolConfig.setJmxEnabled(false);
        ThriftPooledObjectFactory thriftPooledObjectFactory = new ThriftPooledObjectFactory();
        thriftPooledObjectFactory.setLoadBalancerClient(loadBalancerClient);
        thriftPooledObjectFactory.setPropertyResolver(propertyResolver);
        thriftPooledObjectFactory.setProtocolFactory(protocolFactory);
        thriftPooledObjectFactory.setTracer(tracer);
        thriftPooledObjectFactory.setSpanInjector(thriftTransportSpanInjector);
        return new ThriftPool(thriftPooledObjectFactory, poolConfig);
    }
}