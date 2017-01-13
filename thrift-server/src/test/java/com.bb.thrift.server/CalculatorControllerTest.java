package com.bb.thrift.server;

import com.bb.thrift.calculator.TCalculatorService;
import com.bb.thrift.calculator.TOperation;
import com.bb.thrift.server.controller.HCalculatorController;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * Created by bob on 17/1/11.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ThriftApp.class)
@WebAppConfiguration
@IntegrationTest("server.port:0")
public class CalculatorControllerTest {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TProtocolFactory protocolFactory;

    @Autowired
    HCalculatorController hCalculatorController;

    TCalculatorService.Iface client;

    @Before
    public void setUp() throws Exception {
        TTransport transport = new THttpClient("http://localhost:" + port + "/thriftcaclcu");
        TProtocol protocol = protocolFactory.getProtocol(transport);
        client = new TCalculatorService.Client(protocol);
    }

    @Test
    public void testThriftCall() throws Exception {
        assertEquals(4, client.calculate(1, 3, TOperation.ADD));
        assertEquals(5, client.calculate(10, 5, TOperation.SUBTRACT));
    }

    @Test
    public void testHttpCall() throws Exception {
        Map<String, Object> caclculocal = hCalculatorController.caclculocal(1, 3, "1");
        assertEquals(4, caclculocal.get("1"));
    }
}