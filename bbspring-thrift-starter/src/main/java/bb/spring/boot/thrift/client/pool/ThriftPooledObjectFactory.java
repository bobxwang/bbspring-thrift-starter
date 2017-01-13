package bb.spring.boot.thrift.client.pool;

import bb.spring.boot.thrift.client.transport.TLoadBalancerClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.thrift.TServiceClient;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.springframework.beans.BeanUtils;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;
import org.springframework.cloud.sleuth.Tracer;
import org.springframework.core.env.PropertyResolver;

/**
 * Created by bob on 17/1/11.
 */
public class ThriftPooledObjectFactory extends BaseKeyedPooledObjectFactory<ThriftKey, TServiceClient> {

    private static final int DEFAULT_CONNECTION_TIMEOUT = 1000;
    private static final int DEFAULT_READ_TIMEOUT = 30000;
    private static final int DEFAULT_MAX_RETRIES = 1;

    private TProtocolFactory protocolFactory;
    private LoadBalancerClient loadBalancerClient;
    private PropertyResolver propertyResolver;
    private Tracer tracer;
    private SpanInjector<TTransport> spanInjector;

    public void setProtocolFactory(TProtocolFactory protocolFactory) {
        this.protocolFactory = protocolFactory;
    }

    public void setLoadBalancerClient(LoadBalancerClient loadBalancerClient) {
        this.loadBalancerClient = loadBalancerClient;
    }

    public void setPropertyResolver(PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;
    }

    public void setTracer(Tracer tracer) {
        this.tracer = tracer;
    }

    public void setSpanInjector(SpanInjector<TTransport> spanInjector) {
        this.spanInjector = spanInjector;
    }

    @Override
    public void activateObject(ThriftKey key, PooledObject<TServiceClient> p) throws Exception {
        super.activateObject(key, p);

        ThriftPooledObject<TServiceClient> pooledObject = (ThriftPooledObject<TServiceClient>) p;
        Span span = this.tracer.createSpan(key.getServiceName());
        span.logEvent(Span.CLIENT_SEND);
        pooledObject.setSpan(span);
        TTransport transport = pooledObject.getObject().getOutputProtocol().getTransport();
        spanInjector.inject(span, transport);
    }

    @Override
    public void passivateObject(ThriftKey key, PooledObject<TServiceClient> p) throws Exception {
        ThriftPooledObject<TServiceClient> pooledObject = (ThriftPooledObject<TServiceClient>) p;
        TTransport transport = pooledObject.getObject().getOutputProtocol().getTransport();
        if (transport instanceof THttpClient) {
            ((THttpClient) transport).setCustomHeaders(null);
        } else {
            ((TLoadBalancerClient) transport).setCustomHeaders(null);
        }
        resetAndClose(p);
        if (this.tracer.isTracing() && pooledObject.getSpan() != null) {
            Span span = pooledObject.getSpan();
            span.logEvent(Span.CLIENT_RECV);
            this.tracer.close(span);
        }

        super.passivateObject(key, p);
    }

    @Override
    public TServiceClient create(ThriftKey thriftKey) throws Exception {
        String serviceName = thriftKey.getServiceName();
        
        String endpoint = propertyResolver.getProperty(serviceName + ".endpoint");
        int connectTimeout = propertyResolver.getProperty(serviceName + ".connectTimeout", Integer.class, DEFAULT_CONNECTION_TIMEOUT);
        int readTimeout = propertyResolver.getProperty(serviceName + ".readTimeout", Integer.class, DEFAULT_READ_TIMEOUT);
        int maxRetries = propertyResolver.getProperty(serviceName + ".maxRetries", Integer.class, DEFAULT_MAX_RETRIES);

        TProtocol protocol;
        if (StringUtils.isEmpty(endpoint)) {
            final TLoadBalancerClient loadBalancerClient = new TLoadBalancerClient(
                    this.loadBalancerClient,
                    serviceName,
                    propertyResolver.getProperty(serviceName + ".path", "") + thriftKey.getPath()
            );
            loadBalancerClient.setConnectTimeout(connectTimeout);
            loadBalancerClient.setReadTimeout(readTimeout);
            loadBalancerClient.setMaxRetries(maxRetries);
            protocol = protocolFactory.getProtocol(loadBalancerClient);
        } else {
            final THttpClient httpClient = new THttpClient(endpoint);
            httpClient.setConnectTimeout(connectTimeout);
            httpClient.setReadTimeout(readTimeout);
            protocol = protocolFactory.getProtocol(httpClient);
        }

        return BeanUtils.instantiateClass(thriftKey.getClazz().getConstructor(TProtocol.class), (TProtocol) protocol);
    }

    @Override
    public PooledObject<TServiceClient> wrap(TServiceClient tServiceClient) {
        return new ThriftPooledObject<>(tServiceClient);
    }

    private void resetAndClose(PooledObject<TServiceClient> p) {
        p.getObject().getInputProtocol().reset();
        p.getObject().getOutputProtocol().reset();
        p.getObject().getInputProtocol().getTransport().close();
        p.getObject().getOutputProtocol().getTransport().close();
    }
}