package bb.spring.boot.thrift.client.annotation;

import java.lang.annotation.*;

/**
 * according this Annotation to register bean in spring bean factory through ThriftClientAnnotationBeanPostProcessor
 * Created by bob on 17/1/11.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface ThriftClient {

    /**
     * the serviceId registered in spring cloud consul or eureka
     *
     * @return
     */
    String serviceId() default "";

    /**
     * thrift access address
     *
     * @return
     */
    String path() default "";
}