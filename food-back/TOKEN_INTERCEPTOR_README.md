# Token验证拦截器使用说明

## 功能概述

本系统实现了基于JWT Token的请求拦截验证功能，主要特性包括：

- **自动拦截验证**：拦截所有需要认证的请求
- **放行认证接口**：自动放行登录、注册等认证相关接口
- **多种token获取方式**：支持Authorization Header和URL参数两种方式
- **用户信息传递**：验证通过后将用户信息存入请求上下文

## 核心组件

### 1. JwtInterceptor (拦截器)
位置：`common/src/main/java/com/SFood/common/interceptor/JwtInterceptor.java`

主要功能：
- 验证请求中的JWT Token
- 放行认证相关接口
- 将用户信息存入请求属性

### 2. WebMvcConfig (配置类)
位置：`common/src/main/java/com/SFood/common/config/WebMvcConfig.java`

配置拦截规则：
- 拦截所有请求 (`/**`)
- 放行认证接口 (`/auth/**`)
- 放行Swagger文档相关路径

### 3. SecurityContextUtil (工具类)
位置：`common/src/main/java/com/SFood/common/util/SecurityContextUtil.java`

提供便捷方法：
- `getCurrentUserId()` - 获取当前用户ID
- `getCurrentUserPhone()` - 获取当前用户手机号
- `isLoggedIn()` - 检查用户是否已登录

## 使用方式

### 1. 需要token验证的接口

在控制器方法中，可以直接使用`SecurityContextUtil`获取当前用户信息：

```java
@RestController
@RequestMapping("/api")
public class MyController {
    
    @GetMapping("/secure-data")
    public ResultDTO getSecureData() {
        // 获取当前登录用户信息
        Long userId = SecurityContextUtil.getCurrentUserId();
        String phone = SecurityContextUtil.getCurrentUserPhone();
        
        // 业务逻辑...
        return ResultDTO.success("获取数据成功");
    }
}
```

### 2. 公开接口（无需token验证）

将接口路径配置在`/auth/**`路径下，拦截器会自动放行：

```java
@RestController
@RequestMapping("/auth")  // 注意：路径以/auth开头
public class AuthController {
    
    @PostMapping("/login")
    public ResultDTO login(@RequestBody LoginDTO loginDTO) {
        // 登录逻辑，无需token验证
        return ResultDTO.success("登录成功");
    }
}
```

### 3. 请求头设置

客户端需要在请求头中携带token：

```http
GET /api/secure-data HTTP/1.1
Authorization: Bearer your-jwt-token-here
Content-Type: application/json
```

或者通过URL参数传递：

```http
GET /api/secure-data?token=your-jwt-token-here HTTP/1.1
Content-Type: application/json
```

## 放行规则

以下路径会被自动放行（无需token验证）：

- `/auth/**` - 所有认证相关接口
- `/swagger-ui/**` - Swagger UI界面
- `/v3/api-docs/**` - API文档
- `/webjars/**` - WebJars资源
- `/` - 根路径
- `/favicon.ico` - 网站图标

## 错误响应

当token验证失败时，拦截器会返回标准的错误响应：

```json
{
    "code": 401,
    "message": "token无效或已过期",
    "data": null
}
```

## 示例代码

### 测试控制器
位置：`admin/src/main/java/com/SFood/admin/controller/TestController.java`

包含两个示例接口：

1. `/test/secure` - 需要token验证的安全接口
2. `/test/public` - 公开接口（无需token验证）

## 注意事项

1. **包扫描配置**：确保common模块的包被正确扫描
2. **依赖关系**：admin模块需要依赖common模块
3. **Spring Boot版本**：本项目使用Spring Boot 3.x，注意Jakarta EE包名
4. **开发环境**：拦截器会自动放行Swagger相关路径，方便开发调试

## 扩展建议

1. **权限控制**：可以在拦截器中添加基于角色的权限验证
2. **Token刷新**：实现token自动刷新机制
3. **日志记录**：添加请求日志和token使用日志
4. **限流控制**：结合限流机制防止恶意请求