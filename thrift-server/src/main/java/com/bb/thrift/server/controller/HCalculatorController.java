package com.bb.thrift.server.controller;

import bb.spring.boot.thrift.client.annotation.ThriftClient;
import com.bb.thrift.calculator.TCalculatorService;
import com.bb.thrift.calculator.TOperation;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by bob on 17/1/11.
 */
@RestController
public class HCalculatorController {

    @ThriftClient(serviceId = "bbthriftserver", path = "/thriftcaclcu")
    TCalculatorService.Client tcalculatorClient;

    @RequestMapping(method = RequestMethod.GET, value = "/httpcaclcu/{num1}/{num2}/{operation}")
    public Object caclcu(@PathVariable("num1") int m, @PathVariable("num2") int n,
                         @PathVariable("operation") String op) throws Exception {
        Map<String, Object> o = new HashMap<>();
        switch (op) {
            case "1":
                o.put(op, tcalculatorClient.calculate(m, n, TOperation.ADD));
                break;
            case "2":
                o.put(op, tcalculatorClient.calculate(m, n, TOperation.SUBTRACT));
                break;
            case "3":
                o.put(op, tcalculatorClient.calculate(m, n, TOperation.MULTIPLY));
                break;
            case "4":
                o.put(op, tcalculatorClient.calculate(m, n, TOperation.DIVIDE));
                break;
            default:
                o.put(op, op);
                break;
        }
        return o;
    }

    @RequestMapping(method = RequestMethod.GET, value = "/lhttpcaclcu/{num1}/{num2}/{operation}")
    public Map<String, Object> caclculocal(@PathVariable("num1") int m, @PathVariable("num2") int n,
                              @PathVariable("operation") String op) throws Exception {
        Map<String, Object> o = new HashMap<>();
        switch (op) {
            case "1":
                o.put(op, m + n);
                break;
            case "2":
                o.put(op, m - n);
                break;
            case "3":
                o.put(op, m * n);
                break;
            case "4":
                o.put(op, m / n);
                break;
            default:
                o.put(op, op);
                break;
        }
        return o;
    }

}