# **\Spring Cloud：将Spring Boot程序微服务化\**

***\*实验内容\****

 

从GitHub上选择一个简单的Spring Boot程序（如下TODO列表应用），使用Spring Cloud将其微服务化，

代码仓库地址：https://github.com/MikeAScott/spring-todo-list

微服务化包括服务注册（如Nacos）、配置管理（如Nacos）和网关路由（如Spring Cloud Gateway）等各个组件。

评分标准为100分，代码占60分，文档占40分，评估功能完整性、Spring Cloud组件使用和文档清晰度。

 

***\*评分标准\****

评分采用100分制，分为代码（60分）和文档（40分）两部分，具体如下：

 

（1）代码部分（60分）

 

微服务实现（30分）：（1）***\*按照领域合理划分，\*******\*每个微服务功能正常，服务独立运行\*******\*，\*******\*正确使用\*******\*Nacos\*******\*和API网关\*******\*等组件\*******\*，服务间通信有效。\*******\*（2）参考实验中使用的Fenix's Bookstore微服务工程，为待办列表的微服务项目添加用户体系功能，包含用户的增删改查以及鉴权登录。\****

代码质量（10分）：代码结构清晰，命名规范，包含适当的日志和异常处理。

测试覆盖（10分）：包含单元测试和集成测试，覆盖主要功能。

开发工具（10分）：使用Git代码管理工具， CI/CD工具

 

（2）文档部分（40分）

架构描述（10分）：清晰说明微服务架构，包括交互图。

K3S部署方案说明（10分）：提供详细的运行环境设置和配置步骤。部署无错误，服务运行正常。配置清单格式正确，标签和选择器使用得当，端口和服务配置准确

API文档（10分）：记录所有API，包括请求方法、参数和响应示例。

使用指南（10分）：提供应用使用示例和场景说明。

 

![img](file:///C:\Users\肖宇轩\AppData\Local\Temp\ksohtml34136\wps3.jpg) 

![img](file:///C:\Users\肖宇轩\AppData\Local\Temp\ksohtml34136\wps4.jpg) 

 

![img](file:///C:\Users\肖宇轩\AppData\Local\Temp\ksohtml34136\wps5.jpg) 

 

server_tokens off;

![img](file:///C:\Users\肖宇轩\AppData\Local\Temp\ksohtml34136\wps6.jpg) 

 

 error_page 403 /403.html;

​    location = /403.html {

​      root /usr/share/nginx/html;

​      internal;  # 防止直接访问

​    }