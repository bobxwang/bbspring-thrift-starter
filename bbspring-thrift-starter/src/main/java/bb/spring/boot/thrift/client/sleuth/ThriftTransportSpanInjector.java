package bb.spring.boot.thrift.client.sleuth;

import bb.spring.boot.thrift.client.transport.TLoadBalancerClient;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.springframework.cloud.sleuth.Span;
import org.springframework.cloud.sleuth.SpanInjector;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by bob on 17/1/11.
 */
public class ThriftTransportSpanInjector implements SpanInjector<TTransport> {

    @Override
    public void inject(Span span, TTransport tTransport) {
        if (tTransport instanceof THttpClient) {
            ((THttpClient) tTransport).setCustomHeaders(sleuthHeaders(span));
        } else if (tTransport instanceof TLoadBalancerClient) {
            ((TLoadBalancerClient) tTransport).setCustomHeaders(sleuthHeaders(span));
        } else {
            // ignore
        }
    }

    private Map<String, String> sleuthHeaders(Span span) {
        Map<String, String> headers = new HashMap<>();
        if (span == null) {
            headers.put(Span.SAMPLED_NAME, Span.SPAN_NOT_SAMPLED);
        } else {
            headers.put(Span.TRACE_ID_NAME, Span.idToHex(span.getTraceId()));
            headers.put(Span.SPAN_NAME_NAME, span.getName());
            headers.put(Span.SPAN_ID_NAME, Span.idToHex(span.getSpanId()));
            headers.put(Span.SAMPLED_NAME, span.isExportable() ? Span.SPAN_SAMPLED : Span.SPAN_NOT_SAMPLED);
            Long parentId = getParentId(span);
            if (parentId != null) {
                headers.put(Span.PARENT_ID_NAME, Span.idToHex(parentId));
            }
            headers.put(Span.PROCESS_ID_NAME, span.getProcessId());
        }
        return headers;
    }

    private Long getParentId(Span span) {
        return !span.getParents().isEmpty() ? span.getParents().get(0) : null;
    }
}