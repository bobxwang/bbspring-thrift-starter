# 整合Apache Thrift到Spring Cloud

#### 服务端 

* 参照RestController跟RequestMapping添加ThriftController注解

  <pre>@ThriftController(value = "/thriftcaclcu")
  public class CalculatorController implements TCalculatorService.Iface {    
	@Override    
	public int calculate(int num1, int num2, TOperation op) throws TException{ 
		// your code
	}
  }
  </pre>
 
* 添加[spring cloud sleuth]("https://cloud.spring.io/spring-cloud-sleuth")支持

#### 客户端
* 参照[FeignClient]("https://github.com/OpenFeign/feign/")添加ThriftClient注解
	
  <pre>    
  @ThriftClient(serviceId = "bbthriftserver", path = "/thriftcaclcu")
  TCalculatorService.Client tcalculatorClient;
  </pre>
* 相关配置
  <pre>bbthriftserver:
	endpoint:  #cloud情况下默认为空，非空就是直连
	connectTimeout: 1000
	readTimeout: 10000    
  thrift.client.max.threads: 10  #默认即10
  </pre>
  
#### 已有功能
* 扩展**TTransport**支持从注册中心获取服务列表并根据既定策略进行负载，详见**TLoadBalancerClient**类，利用[Netflix](https://github.com/netflix/ribbon)
* 服务端添加Metrics支持，统计各服务响应时间及各方法调用次数 
* 封装**TServiceClient**支持超时熔断保护利用[Hystrix](https://github.com/netflix/hystrix)

#### 待做功能 
* 扩展**TProtocol**支持调用信息采集操作
* 扩展**TProcessor**支持信息采集操作及安全监测
* 服务请求链路上下文调用链路传递

#### 工程简述
* thrift-definition thrift定义文件所在工程
* thrift-server是一个关于如何使用的例子，需要在本地启动一个consul agent，里面的常规restcontroller中模拟一个向thriftcontroller请求的例子
