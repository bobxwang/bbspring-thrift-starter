package bb.spring.boot.thrift.client.transport;

import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 参照THttpClient,支持从注册中心获取服务列表，并根据既定的策略进行负载均衡,利用LoadBalancerClient
 * <p>
 * Created by bob on 17/1/11.
 */
public class TLoadBalancerClient extends TTransport {

    private LoadBalancerClient loadBalancerClient;
    private String serviceName;
    private String path;
    private final ByteArrayOutputStream requestBuffer_ = new ByteArrayOutputStream();
    private InputStream inputStream_ = null;
    private int connectTimeout_ = 0;
    private int readTimeout_ = 0;
    private int maxRetries_ = 1;
    private Map<String, String> customHeaders_ = null;

    public TLoadBalancerClient(LoadBalancerClient loadBalancerClient, String serviceName, String path) {
        this.loadBalancerClient = loadBalancerClient;
        this.serviceName = serviceName;
        this.path = path;
    }

    public void setConnectTimeout(int timeout) {
        this.connectTimeout_ = timeout;
    }

    public void setReadTimeout(int timeout) {
        this.readTimeout_ = timeout;
    }

    public void setMaxRetries(int maxRetries) {
        if (maxRetries <= 0) {
            throw new RuntimeException("Illegal maxRetries value [" + maxRetries + "]. Positive value expected");
        }
        this.maxRetries_ = maxRetries;
    }

    public void setCustomHeaders(Map<String, String> headers) {
        this.customHeaders_ = headers;
    }

    public void setCustomHeader(String key, String value) {
        if (this.customHeaders_ == null) {
            this.customHeaders_ = new HashMap();
        }
        this.customHeaders_.put(key, value);
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void open() throws TTransportException {
    }

    @Override
    public void close() {
        if (null != this.inputStream_) {
            try {
                this.inputStream_.close();
            } catch (IOException ioe) {
            }
            this.inputStream_ = null;
        }
    }

    @Override
    public int read(byte[] buf, int off, int len) throws TTransportException {
        if (this.inputStream_ == null) {
            throw new TTransportException("Response buffer is empty, no request.");
        } else {
            try {
                int iox = this.inputStream_.read(buf, off, len);
                if (iox == -1) {
                    throw new TTransportException("No more data available.");
                } else {
                    return iox;
                }
            } catch (IOException ioe) {
                throw new TTransportException(ioe);
            }
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) throws TTransportException {
        this.requestBuffer_.write(buf, off, len);
    }

    @Override
    public void flush() throws TTransportException {
        super.flush();
        byte[] data = this.requestBuffer_.toByteArray();
        this.requestBuffer_.reset();
        int retryCount = 0;
        while (true) {
            try {
                retryCount++;
                doFlush(data);
                return;
            } catch (IOException ioe) {
                if (retryCount >= maxRetries_) {
                    throw new TTransportException(ioe);
                }
            } catch (Exception e) {
                if (retryCount >= maxRetries_) {
                    throw e;
                }
            }
        }
    }

    private void doFlush(byte[] data) throws TTransportException, IOException {
        ServiceInstance serviceInstance = this.loadBalancerClient.choose(serviceName);
        if (serviceInstance == null) {
            throw new TTransportException(TTransportException.NOT_OPEN, "No service instances available");
        }

        HttpURLConnection iox = (HttpURLConnection) new URL(serviceInstance.getUri().toString() + path).openConnection();
        if (this.connectTimeout_ > 0) {
            iox.setConnectTimeout(this.connectTimeout_);
        }
        if (this.readTimeout_ > 0) {
            iox.setReadTimeout(this.readTimeout_);
        }

        iox.setRequestMethod("POST");
        iox.setRequestProperty("Content-Type", "application/x-thrift");
        iox.setRequestProperty("Accept", "application/x-thrift");
        iox.setRequestProperty("User-Agent", "Java/THttpClient");
        if (this.customHeaders_ != null) {
            Iterator responseCode = this.customHeaders_.entrySet().iterator();
            while (responseCode.hasNext()) {
                Map.Entry header = (Map.Entry) responseCode.next();
                iox.setRequestProperty((String) header.getKey(), (String) header.getValue());
            }
        }
        iox.setDoOutput(true);
        iox.connect();
        iox.getOutputStream().write(data);
        int responseCode1 = iox.getResponseCode();
        if (responseCode1 != 200) {
            throw new TTransportException("HTTP Response code: " + responseCode1);
        } else {
            this.inputStream_ = iox.getInputStream();
        }
    }
}