package com.bb.thrift.server.thriftcontroller;

import bb.spring.boot.thrift.server.annotation.ThriftController;
import com.bb.thrift.calculator.TCalculatorService;
import com.bb.thrift.calculator.TDivisionByZeroException;
import com.bb.thrift.calculator.TOperation;
import org.apache.thrift.TException;

import java.util.Random;

/**
 * Created by bob on 17/1/11.
 */
@ThriftController(value = "/thriftcaclcu")
public class CalculatorController implements TCalculatorService.Iface {

    @Override
    public int calculate(int num1, int num2, TOperation op) throws TDivisionByZeroException, TException {
        int r = 0;
        switch (op) {
            case ADD:
                r = num1 + num2;
                break;
            case SUBTRACT:
                try {
                    Thread.sleep(new Random().nextInt(1000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                r = num1 - num2;
                break;
            case MULTIPLY:
                r = num1 * num2;
                break;
            case DIVIDE:
                r = num1 / num2;
                break;
        }
        return r;
    }
}