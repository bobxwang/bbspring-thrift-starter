package bb.spring.boot.thrift.client.pool;

import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.thrift.TServiceClient;

/**
 * Created by bob on 17/1/11.
 */
public class ThriftPool extends GenericKeyedObjectPool<ThriftKey, TServiceClient> {

    public ThriftPool(KeyedPooledObjectFactory<ThriftKey, TServiceClient> factory,
                      GenericKeyedObjectPoolConfig config) {
        super(factory, config);
    }
}