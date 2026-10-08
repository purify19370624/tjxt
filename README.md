# 天机学堂（tjxt）

天机学堂是一套面向在线职业教育的**微服务后端平台**，基于 Spring Cloud Alibaba 构建，覆盖课程、学习、考试、交易、支付、营销、消息、数据看板等完整业务域。项目采用 Maven 多模块结构，共 16 个一级模块、约 750 个 Java 源文件。

> 本项目源自传智教育「天机学堂」教学项目，用于微服务架构学习与实践。

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [服务与端口](#服务与端口)
- [项目结构](#项目结构)
- [环境准备](#环境准备)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [网关路由与接口文档](#网关路由与接口文档)
- [开发约定](#开发约定)
- [部署](#部署)
- [常见问题](#常见问题)

---

## 功能概览

| 业务域 | 能力 |
| --- | --- |
| 认证授权 | 账号登录（学员 / 管理端）、JWT（RS256）签发与刷新、菜单、角色、接口权限校验、JWKS 公钥分发 |
| 用户中心 | 学员、教师、员工三类用户，注册、登录、资料与状态管理 |
| 课程中心 | 课程、课程分类、题目/目录（章节）、课程上下架与状态流转 |
| 媒资管理 | 文件上传与视频媒资（腾讯云 COS / VOD 与阿里云 OSS 双实现，可配置切换）、上传/播放/预览签名、VOD 事件回调轮询 |
| 课程搜索 | 基于 Elasticsearch 的课程检索、兴趣分类、推荐 |
| 学习中心 | 学习记录与进度（Redis + 本地延迟队列合并写库）、积分体系、签到（Redis BitMap）、积分榜（Redis ZSet + 分表落库）、互动问答（提问/回答） |
| 考试中心 | 题目管理、题目业务（作答、判分、统计） |
| 交易中心 | 购物车、下单、订单查询、退款申请 |
| 支付中心 | 支付渠道、支付单、退款单、支付宝/微信回调与对账 |
| 促销营销 | 优惠券（发放、领取、核销）、兑换码、优惠计算策略 |
| 消息通知 | 短信（阿里云）、站内信、通知模板与通知任务 |
| 点赞互动 | 点赞记录、点赞数异步汇总 |
| 数据看板 | 今日数据、TOP10 排行、ECharts 榜单 |

---

## 技术栈

| 分类 | 技术 | 版本 |
| --- | --- | --- |
| 语言 / 构建 | Java、Maven | JDK 11、Maven 3.8+ |
| 基础框架 | Spring Boot | 2.7.2 |
| 微服务 | Spring Cloud | 2021.0.3 |
| 微服务 | Spring Cloud Alibaba | 2021.0.1.0 |
| 注册 / 配置中心 | Nacos（discovery + config） | 由 SCA 管理 |
| 网关 | Spring Cloud Gateway | 由 SC 管理 |
| 服务调用 | OpenFeign + LoadBalancer + Sentinel（降级/熔断） | 由 SC / SCA 管理 |
| 持久层 | MyBatis-Plus | 3.4.3 |
| 数据库 | MySQL（`mysql-connector-java`） | 8.0.23 |
| 缓存 | Redis + Redisson（分布式锁） | Redisson 3.13.6 |
| 消息队列 | RabbitMQ（spring-amqp / spring-rabbit） | 由 Boot 管理 |
| 搜索引擎 | Elasticsearch（rest-high-level-client） | 7.12.1 |
| 分布式事务 | Seata（`@GlobalTransactional`） | 1.7.0 |
| 定时任务 | XXL-JOB | 2.3.1 |
| 本地缓存 | Caffeine | 由 Boot 管理 |
| 接口文档 | Knife4j / Swagger（springfox） | 3.0.3 |
| 工具库 | Hutool（含 JWT） | 5.7.17 |
| 代码简化 | Lombok | 1.18.20 |
| 第三方云服务 | 腾讯云 COS / VOD、阿里云短信 / OSS、支付宝、微信支付 | 见根 `pom.xml` |

---

## 服务与端口

### 业务服务

| 模块 | 服务名（`spring.application.name`） | 端口 | 数据库 | 职责 |
| --- | --- | --- | --- | --- |
| `tj-gateway` | `gateway-service` | **10010** | — | API 网关：路由转发、跨域、登录与权限校验、请求 ID 透传、异常统一处理、Swagger 聚合 |
| `tj-auth` | `auth-service` | 8081 | `tj_auth` | 认证授权：登录、JWT 签发/刷新、菜单、角色、权限、JWKS |
| `tj-user` | `user-service` | 8082 | `tj_user` | 用户中心：学员 / 教师 / 员工 |
| `tj-search` | `search-service` | 8083 | `tj_search` | 课程搜索与推荐（Elasticsearch） |
| `tj-media` | `media-service` | 8084 | `tj_media` | 媒资：文件与视频上传、播放签名 |
| `tj-message` | `message-service` | 8085 | `tj_message` | 消息：短信、站内信、通知任务 |
| `tj-course` | `course-service` | 8086 | `tj_course` | 课程中心 |
| `tj-pay` | `pay-service` | 8087 | `tj_pay` | 支付中心 |
| `tj-trade` | `trade-service` | 8088 | `tj_trade` | 交易中心：购物车、订单、退款 |
| `tj-exam` | `exam-service` | 8089 | `tj_exam` | 考试中心 |
| `tj-learning` | `learning-service` | 8090 | `tj_learning` | 学习中心：学习记录、积分、签到、问答 |
| `tj-remark` | `remark-service` | 8091 | `tj_remark` | 点赞 |
| `tj-promotion` | `promotion-service` | 8092 | `tj_promotion` | 促销：优惠券、兑换码 |
| `tj-data` | `data-service` | 8093 | `tj_data` | 数据看板 |

### 公共 / SDK 模块

| 模块 | 说明 |
| --- | --- |
| `tj-common` | 公共基础模块：统一响应 `R`、异常体系与全局异常处理、MyBatis-Plus 插件与自动填充、RabbitMQ 封装、Redisson 分布式锁、Knife4j、XXL-JOB、请求 ID 透传、`UserContext`、工具类。通过 `META-INF/spring.factories` 自动装配，引入即生效 |
| `tj-api` | 跨服务契约：12 个 Feign 客户端、约 30 个共享 DTO、5 个降级工厂、`CategoryCache` / `RoleCache` 本地缓存 |
| `tj-auth/tj-auth-common` | 认证契约：JWT 常量、错误码、`PrivilegeRoleDTO` |
| `tj-auth/tj-auth-gateway-sdk` | 网关侧 SDK：解析 JWT、拉取 JWKS 公钥、按 Redis 权限表做接口鉴权 |
| `tj-auth/tj-auth-resource-sdk` | 微服务侧 SDK：读取 `user-info` 写入 `UserContext`、登录拦截（401）、Feign 用户身份透传 |
| `tj-message/tj-message-api` | 消息服务对外 SDK（Feign 接口 + MQ 常量） |
| `tj-message/tj-message-domain` | 消息服务领域模型 |
| `tj-pay/tj-pay-api` | 支付服务对外 SDK（`PayClient`） |
| `tj-pay/tj-pay-domain` | 支付服务领域模型 |

> `tj-auth`、`tj-message`、`tj-pay` 是聚合模块（`pom` 打包），其余服务为单模块。

### 技术要点分布

下表按**代码中的实际使用**统计（部分模块在 `pom.xml` / `bootstrap.yml` 中引入了依赖，但源码未实际使用，已在备注中标注）。

| 能力 | 实际使用的服务 | 说明 |
| --- | --- | --- |
| RabbitMQ | course、search、message、trade、pay、learning、remark；user（经 `AsyncSmsClient` 间接发布） | 事件驱动：课程上下架、订单支付/退款、点赞数变更、短信异步发送、订单超时延迟队列 |
| Elasticsearch | search | `RestHighLevelClient` 建索引 / 检索 / 高亮 |
| Seata | course（`CourseDraftServiceImpl` 的 `@GlobalTransactional`） | exam 引入了依赖但无注解 |
| XXL-JOB | learning(3)、pay(2)、course(1)、message(1)、trade(1) | promotion、exam、data 引入了依赖但无 `@XxlJob` 处理器 |
| Redisson `@Lock` | pay（支付申请、退款申请、回调幂等、对账任务） | 能力由 `tj-common` 提供；其余模块引入了依赖但未使用 |
| Caffeine 本地缓存 | api（`CategoryCache` / `RoleCache`，被 user、trade、exam、learning、search、promotion 复用）、message | 缓存分类与角色、短信平台配置，30 分钟过期且无主动失效 |
| Sentinel（Feign 降级） | api（提供降级工厂）、trade、pay | 降级开关在 Nacos `shared-feign.yaml` |
| Redis | 除 media 外的服务 | 签到 BitMap、积分榜 ZSet、兑换码 Bitmap、验证码、点赞 Set/ZSet、看板 Hash、学习记录 Hash |

> 项目中没有使用 WebSocket，也没有使用 Lua 脚本；需要原子性的场景通过 Redis BitMap / ZSet / 自增实现。

---

## 项目结构

```
tianji/
├── pom.xml                     # 根 POM：依赖版本统一管理
├── Dockerfile                  # 单服务镜像构建（openjdk:11.0-jre-buster）
├── startup.sh                  # 构建镜像并启动容器的发布脚本
├── tj-common/                  # 公共基础模块
│   └── src/main/
│       ├── java/com/tianji/common/
│       │   ├── autoconfigure/  # 自动装配：mq / mvc / mybatis / redisson / swagger / xxljob
│       │   ├── constants/      # Constant、ErrorInfo、MqConstants、RegexConstants
│       │   ├── domain/         # R、LoginUserDTO、PageQuery、PageDTO
│       │   ├── enums/          # BaseEnum、CommonStatus、UserType
│       │   ├── exceptions/     # CommonException 及其子类
│       │   ├── filters/        # RequestIdFilter
│       │   ├── utils/          # UserContext、JsonUtils、BeanUtils、DateUtils 等
│       │   └── validate/       # @ParamChecker、@EnumValid
│       └── resources/META-INF/ # spring.factories、spring-configuration-metadata.json
├── tj-api/                     # Feign 客户端、DTO、降级、本地缓存
├── tj-auth/                    # 认证授权
│   ├── tj-auth-common/         #   常量与契约
│   ├── tj-auth-gateway-sdk/    #   网关侧鉴权 SDK
│   ├── tj-auth-resource-sdk/   #   业务服务侧登录拦截 SDK
│   └── tj-auth-service/        #   auth-service 服务本体
├── tj-gateway/                 # 网关
├── tj-user/  tj-course/  tj-search/  tj-media/  tj-message/
├── tj-trade/ tj-pay/     tj-exam/    tj-learning/
├── tj-remark/ tj-promotion/ tj-data/
├── logs/                       # 运行日志目录（git 忽略）
└── job/                        # XXL-JOB 执行器日志目录（git 忽略）
```

---

## 环境准备

### 1. 基础环境

| 组件 | 版本要求 | 说明 |
| --- | --- | --- |
| JDK | **11**（与 `maven.compiler.source/target` 一致） | 不建议使用 17+：Lombok 1.18.20 在较新 JDK 上无法编译 |
| Maven | 3.8+ | 项目未提供 `mvnw`，需本地安装 |
| MySQL | 8.x | 需创建 13 个业务库，库名见[服务与端口](#服务与端口) |
| Redis | 5.x+ | 缓存、分布式锁、权限表、JWT 会话 |
| RabbitMQ | 3.8+ | 需创建 vhost `/tjxt`；**必须开启 `rabbitmq_delayed_message_exchange` 插件**（订单超时取消依赖 `x-delay` 头） |
| Nacos | 2.x | 同时作为注册中心与配置中心 |
| Elasticsearch | 7.12.x | 仅 `search-service` 需要 |
| Seata Server | 1.7.0 | 仅 `course-service` 实际使用（`exam-service` 的 `bootstrap.yml` 引入了 `shared-seata.yaml`，但源码中无 `@GlobalTransactional`） |
| XXL-JOB Admin | 2.3.1 | 需在调度中心注册执行器的服务：course、learning、trade、pay、message（exam、promotion、data 引入了 `shared-xxljob.yaml` 但源码中无 `@XxlJob` 处理器） |

### 2. 默认连接信息

仓库中 `bootstrap-dev.yml` / `bootstrap-local.yml` 的默认指向如下，按需修改：

```yaml
spring:
  cloud:
    nacos:
      server-addr: 192.168.150.101:8848   # Nacos 地址
      discovery:
        namespace: f923fb34-cb0a-4c06-8fca-ad61ea61a3f0
        group: DEFAULT_GROUP
```

`tj-common` 中公共组件还定义了以下默认值与配置前缀（详见 `tj-common/src/main/resources/META-INF/spring-configuration-metadata.json`）：

| 配置前缀 | 用途 | 默认值 |
| --- | --- | --- |
| `tj.jdbc.*` | 数据库连接 | host `192.168.150.101`、port `3306`、username `root`、password `123` |
| `tj.redis.*` | Redis 连接与连接池 | host `192.168.150.101`、password `123321` |
| `tj.mq.*` | RabbitMQ 连接与消费重试 | host `192.168.150.101`、port `5672`、vhost `/tjxt`、username `tjxt`、password `123321` |
| `tj.swagger.*` | 接口文档开关与信息 | `enable=false` |
| `tj.xxl-job.*` | XXL-JOB 执行器 | — |

> 各服务 `bootstrap.yml` 中只声明 `tj.jdbc.database`（库名）与 `tj.swagger.*`；具体连接串由 Nacos 共享配置提供，因此**仓库内看不到完整数据源配置**，需要在 Nacos 中自行维护。

---

## 快速开始

### 第 1 步：准备中间件

按上文清单启动 MySQL、Redis、RabbitMQ、Nacos（以及需要时的 Elasticsearch、Seata、XXL-JOB），并保证网络可达。

### 第 2 步：初始化数据库

为每个服务创建独立数据库，并导入对应的建表脚本（脚本未包含在本仓库中）：

```
tj_auth  tj_user  tj_search  tj_media  tj_message  tj_course  tj_pay
tj_trade tj_exam  tj_learning tj_remark tj_promotion tj_data
```

### 第 3 步：配置 Nacos

在目标命名空间（`f923fb34-cb0a-4c06-8fca-ad61ea61a3f0`）、`DEFAULT_GROUP`、`yaml` 格式下创建**共享配置**。各服务按需引用，data-id 如下：

| data-id | 内容 |
| --- | --- |
| `shared-spring.yaml` | 公共 Spring 配置 |
| `shared-redis.yaml` | Redis 连接、连接池 |
| `shared-mybatis.yaml` | 数据源、MyBatis-Plus 配置 |
| `shared-logs.yaml` | 日志配置 |
| `shared-feign.yaml` | Feign / Sentinel 降级相关配置 |
| `shared-mq.yaml` | RabbitMQ 连接、消费者重试 |
| `shared-seata.yaml` | Seata 事务组配置 |
| `shared-xxljob.yaml` | XXL-JOB 执行器配置 |

此外可为各服务创建以服务名命名的配置（如 `gateway-service.yaml`、`user-service.yaml`），用于存放服务独有配置——**网关的免登录白名单 `tj.auth.excludePath` 即在此维护**（ant 风格，支持 `METHOD:/path` 形式，例如 `POST:/us/students/register`、`GET:/us/users/{id}`）。

### 第 4 步：修改本地连接信息

按实际环境修改每个服务 `src/main/resources/bootstrap-dev.yml` 中的 `spring.cloud.nacos.server-addr`（或在 IDE 中以 `local` profile 启动，改用 `bootstrap-local.yml`）。

> 默认激活的 profile 是 `dev`（见各服务 `bootstrap.yml` 的 `spring.profiles.active`）。

### 第 5 步：编译

```bash
# 在项目根目录执行，先安装公共模块
mvn clean install -DskipTests
```

编译产物为各模块 `target/{artifactId}.jar`，例如 `tj-gateway/target/tj-gateway.jar`、`tj-auth/tj-auth-service/target/tj-auth-service.jar`。

### 第 6 步：启动服务

在 IDE 中直接运行各服务的 `*Application` 主类，或使用命令行：

```bash
java -jar tj-gateway/target/tj-gateway.jar
```

建议启动顺序：

1. `auth-service`（网关启动后会从它的 `/jwks` 拉取公钥，未就绪时每 10 秒重试）
2. `user-service`、`course-service` 等业务服务（顺序不限）
3. `gateway-service`

### 第 7 步：访问

- 统一入口（网关）：`http://localhost:10010`
- 各服务接口文档（Knife4j）：`http://localhost:{port}/doc.html`，例如课程服务 `http://localhost:8086/doc.html`
- 网关聚合的文档资源列表：`http://localhost:10010/swagger-resources`

---

## 配置说明

### 配置加载顺序

1. 各服务 `src/main/resources/bootstrap.yml`：端口、服务名、激活 profile、网关路由（仅网关）、`tj.*` 业务配置
2. `bootstrap-{profile}.yml`：Nacos 地址、命名空间、注册 IP
3. Nacos 中的 `{spring.application.name}.yaml`：服务独有配置
4. Nacos 中的 `shared-*.yaml`：共享配置（`refresh: false`，不动态刷新）

### 各服务 `tj.*` 配置摘要

| 服务 | `tj.jdbc.database` | 特有配置 |
| --- | --- | --- |
| auth | `tj_auth` | `encrypt.key-store.*`（JWT 签名 KeyStore，`classpath:tjxt.jks`）；`tj.auth.resource.enable=true` + `includeLoginPaths` |
| user | `tj_user` | `tj.auth.resource.excludeLoginPaths` |
| search | `tj_search` | `tj.auth.resource.includeLoginPaths`；`spring.elasticsearch.uris` |
| media | `tj_media` | `tj.platform.file/media=TENCENT`、`tj.tencent.vod.*`、`tj.tencent.cos.*`；`excludeLoginPaths: /medias/signature/play` |
| message | `tj_message` | `tj.sms.ali.*`；`excludeLoginPaths` |
| course | `tj_course` | `tj.auth.resource.enable=false`；`tj.main.allow-circular-references=true` |
| pay | `tj_pay` | `tj.pay.notifyHost`、`tj.pay.ali.*`、`tj.pay.wx.*`（未开启 resource 登录拦截） |
| trade | `tj_trade` | `excludeLoginPaths: /order-details/enrollNum` |
| exam | `tj_exam` | `tj.auth.resource.enable=false` |
| learning | `tj_learning` | 开启登录拦截 |
| remark | `tj_remark` | 开启登录拦截 |
| promotion | `tj_promotion` | 开启登录拦截 |
| data | `tj_data` | 开启登录拦截 |

### 敏感配置（环境变量）

为免于把第三方凭证写进源码，以下配置项已改为 `${环境变量:}` 占位符。**本地或部署时必须自行注入，否则相关功能不可用**（未注入时解析为空字符串，服务仍能启动）。

| 环境变量 | 用途 | 所属服务 |
| --- | --- | --- |
| `TENCENT_APP_ID`、`TENCENT_SECRET_ID`、`TENCENT_SECRET_KEY` | 腾讯云 API 凭证 | media |
| `TENCENT_VOD_URL_KEY` | 腾讯云 VOD 播放签名密钥 | media |
| `ALI_SMS_ACCESS_ID`、`ALI_SMS_ACCESS_SECRET` | 阿里云短信凭证 | message |
| `ALI_PAY_APP_ID`、`ALI_PAY_MERCHANT_PRIVATE_KEY`、`ALI_PAY_PUBLIC_KEY` | 支付宝应用与密钥 | pay |
| `WX_PAY_APP_ID`、`WX_PAY_MCH_ID`、`WX_PAY_MCH_SERIAL_NO`、`WX_PAY_PRIVATE_KEY`、`WX_PAY_API_V3_KEY` | 微信支付商户信息与密钥 | pay |
| `PAY_NOTIFY_HOST` | 支付异步通知回调地址前缀 | pay |
| `JWT_KEYSTORE_PASSWORD`、`JWT_KEYSTORE_SECRET` | JWT 签名密钥库（`tjxt.jks`）口令 | auth |

注入方式示例（以 IDEA 为例：Run/Debug Configurations → Environment variables；命令行则为系统环境变量）：

```bash
export ALI_SMS_ACCESS_ID=your-access-id
export ALI_SMS_ACCESS_SECRET=your-access-secret
```

> 也可以把这些值放进 Nacos 配置或启动参数，覆盖 `bootstrap.yml` 中的占位符。**切勿再把真实凭证提交进仓库。**

---

## 网关路由与接口文档

网关使用 `StripPrefix=1` 剥离第一段路径前缀后转发，例如 `/cs/courses/1` → `course-service` 的 `/courses/1`。

| 前缀 | 目标服务 | 前缀 | 目标服务 |
| --- | --- | --- | --- |
| `/as/**` | auth-service | `/ss/**` | search-service |
| `/us/**` | user-service | `/ls/**` | learning-service |
| `/cs/**` | course-service | `/es/**` | exam-service |
| `/ms/**` | media-service | `/ts/**` | trade-service |
| `/sms/**` | message-service | `/ps/**` | pay-service |
| `/ds/**` | data-service | `/prs/**` | promotion-service |
| `/rs/**` | remark-service | `/os/**` | order-service（历史遗留，仓库内无该服务） |

> 网关通过 `RouteLocator` 反推服务名生成聚合文档地址，因此**路由 id 必须与路径前缀一致**（`as` ↔ `/as/**`）。当前配置中 `rs` 存在重复声明。

---

## 开发约定

### 统一响应

所有对外接口返回 `R<T>`：

```json
{
  "code": 200,
  "msg": "OK",
  "data": {},
  "requestId": "1af123c11412e"
}
```

`code` 为 200 表示成功。服务内部由 `WrapperResponseBodyAdvice` 自动包装——仅当请求头带 `x-request-from: gateway`（来自网关）时包装为 `R`，内部 Feign 调用保持原始结构。

### 异常处理

- 业务异常继承 `CommonException`（如 `BadRequestException`、`BizIllegalException`），由 `CommonExceptionAdvice` 统一转换为 `R`。
- 来自网关的请求统一返回 HTTP 200 + 业务 `code`；微服务之间的调用保留原始 HTTP 状态码，以便 Feign 降级识别。

### 登录态与用户透传

```
客户端 → 网关 AccountAuthFilter
         ├─ 校验白名单 tj.auth.excludePath
         ├─ 用从 auth-service /jwks 拉取的 RSA 公钥验签 JWT
         └─ 注入请求头 user-info = userId
       → 业务服务 UserInfoInterceptor → UserContext.setUser(userId)
         ├─ 业务代码通过 UserContext.getUser() 获取当前用户
         ├─ MyBatisAutoFillInterceptor 自动填充 creater / updater
         └─ FeignRelayUserInterceptor 继续向更下游透传 user-info
```

要点：

- 网关是**唯一**的验签与鉴权入口；下游服务不再验签 token。因此业务服务端口不应直接对外暴露。
- `tj.auth.resource.enable=true` 时服务自身再做一次登录拦截（`LoginAuthInterceptor`），可用 `includeLoginPaths` / `excludeLoginPaths` 精确控制。
- JWT 相关常量见 `JwtConstants`：请求头 `authorization`、刷新头 `refresh` / `admin-refresh`、用户头 `user-info`，算法 `rs256`。

### 请求 ID 链路追踪

- 网关 `RequestIdRelayFilter` 生成 UUID 写入 MDC 与请求头 `requestId`（`/ps/notify` 支付回调除外）。
- 业务服务 `RequestIdFilter` 从请求头还原到 MDC。
- Feign 调用由 `RequestIdRelayConfiguration` 透传；MQ 消费端由 `MqConfig` 从消息头还原。
- 返回值中的 `requestId` 便于日志检索。

### 分布式锁

引入 Redisson 的服务可直接使用注解：

```java
@Lock(name = "#id", lockType = LockType.DEFAULT, lockStrategy = LockStrategy.FAIL_AFTER_RETRY_TIMEOUT)
public void doSomething(Long id) { ... }
```

`name` 支持 SpEL；提供可重入/公平/读/写四种锁类型与五种获取策略。

### 消息队列

- 发送：注入 `RabbitMqHelper`，支持普通消息、延迟消息、异步发送。
- 消费：`@RabbitListener` + `@QueueBinding` 声明式绑定。
- 失败重投：消费失败后重投到 `error.topic` 交换机，队列为 `error.{applicationName}.queue`。
- 常量集中在 `MqConstants`。

### 远程调用

- 在 `tj-api` 中定义 `@FeignClient` 接口（按需提供 `fallbackFactory`），业务服务引入 `tj-api` 后直接注入使用。
- 本地缓存工具 `CategoryCache`（课程分类）、`RoleCache`（角色）基于 Caffeine，30 分钟过期，无主动失效。

### 定时任务

- 使用 XXL-JOB，注解 `@XxlJob("handlerName")`，如 `courseFinished`、`savePointsBoard2DB`、`refundOrderCheckHandler`、`publishNoticeJob`。
- 执行器日志输出到项目根目录 `job/{service-name}/`。

### 分页

**MySQL 系（MyBatis-Plus）**：`PaginationInnerInterceptor`（MySQL，`maxLimit=200`，即单页最多 200 条）；查询参数继承 `PageQuery`，响应使用 `PageDTO`。本质仍是 `LIMIT offset, size` 的偏移分页，offset 越大越慢，因此管理端列表应避免直接翻到很深的页码。

**Elasticsearch 系（课程搜索）**：`GET /ss/courses/portal` 同时支持两种翻页方式。

| 方式 | 传参 | 适用场景 | 代价 |
| --- | --- | --- | --- |
| 偏移分页 | `pageNo` + `pageSize` | 浅翻页、需要按页码跳转 | 随页深线性增长；`from + size` 超过 10000 会被拒绝 |
| 游标分页 | `cursor`（取上一页返回的 `nextCursor`） | 深翻页、"加载更多" | 与页深无关，可无限翻页 |

约定：

- 单页最多 **100** 条，超出会被静默截断（避免一个请求拉走整个索引）。
- `from + size > 10000`（ES 的 `index.max_result_window` 默认值）时返回**业务 400**「搜索结果过深…」，而不是把 ES 异常包装成 500。
- 响应体在 `total / pages / list` 之外多返回一个 `nextCursor`；为空表示已是最后一页。把它作为下一页的 `cursor` 参数回传即可继续翻页。
- 排序**始终以课程 `id` 作为决胜字段**，保证同分或排序值相同的文档在翻页时顺序稳定，不会重复出现或漏掉课程——这同时也是 `search_after` 能正确定位的前提。
- 游标模式下 `pageNo` 不再生效（`search_after` 与 `from` 互斥），因此该模式只支持"下一页"，不支持跳页与回退。游标是不透明的 Base64URL 字符串，客户端不应解析其内容。

---

## 部署

`Dockerfile` 用于构建**单个服务**的镜像：它把当前目录下的 `app.jar` 复制进镜像，并以 `java -jar $JAVA_OPTS /app/app.jar` 启动（基础镜像 `openjdk:11.0-jre-buster`，时区 `Asia/Shanghai`）。

`startup.sh` 是配套的发布脚本，把上述流程串起来：

```bash
# -c 容器/镜像名  -n 项目名(jar名)  -d 模块相对路径  -p 端口
./startup.sh -c tj-course -n tj-course -d tj-course -p 8086
```

脚本会依次：从 `${BASE_PATH}/${PROJECT_PATH}/target/${PROJECT_NAME}.jar` 复制 jar 到当前目录并重命名为 `app.jar` → 构建镜像 → 删除同名容器与镜像 → 启动容器。

参数说明：

| 参数 | 含义 |
| --- | --- |
| `-c` | 容器名 / 镜像名 |
| `-n` | 项目名（等于 jar 文件名，如 `tj-course`） |
| `-d` | jar 所在目录相对于 `BASE_PATH` 的路径（脚本会自动追加 `/target`） |
| `-p` | 对外端口 |
| `-o` | JVM 参数（默认 `-Xms300m -Xmx300m`） |
| `-a` | 调试端口；非 0 时以 `jdwp` 调试模式启动并映射 `5005` |

注意事项：

- 脚本中的 `BASE_PATH` 硬编码为 `/usr/local/src/jenkins/workspace/tjxt-dev-build`，需按实际 CI 路径修改。
- 容器默认接入 Docker 网络 `heima-net`，需提前创建；容器内存限制 300m。
- 各服务的端口与 jar 名对应关系见[服务与端口](#服务与端口)。

---

## 常见问题

**1. 编译报错，提示 Lombok / 无法访问某个类**

确认使用 **JDK 11**。项目 `maven.compiler.source/target` 为 11，Lombok 版本 1.18.20 在 JDK 17 及以上无法正常工作。可通过 `JAVA_HOME` 或 IDE 的 Project SDK 切换。

**2. 启动失败，提示找不到某个配置项 / `excludePath` 为空**

Nacos 中的共享配置（`shared-*.yaml`）或服务配置缺失。注意 `AuthProperties.afterPropertiesSet()` 会直接对 `excludePath` 调用 `add`，若 Nacos 中 `tj.auth.excludePath` 未配置会导致启动异常，请在 `gateway-service.yaml` 中至少提供一个空列表。

**3. 启动时报连接超时**

检查 `bootstrap-dev.yml` 中的 Nacos 地址是否与实际环境一致，以及 MySQL / Redis / RabbitMQ 的连接串（位于 Nacos）是否正确。

**4. 端口冲突**

网关固定占用 **10010**，业务服务占用 8081–8093，可用 `netstat -ano | findstr 10010`（Windows）排查。

**5. 第三方密钥与密钥库口令**

源码中的支付宝 / 微信支付私钥、阿里云短信与腾讯云凭证、密钥库口令**已全部替换为环境变量占位符**（见[敏感配置](#敏感配置环境变量)），仓库当前版本不再包含任何真实凭证。

仍请注意两点：

- `tj-auth/tj-auth-service/src/main/resources/tjxt.jks` 是课程自带的**自签演示证书**，为方便开箱运行而保留；正式环境请自行用 `keytool` 生成证书并妥善保管私钥，同时通过 `JWT_KEYSTORE_PASSWORD` / `JWT_KEYSTORE_SECRET` 注入口令。
- 历史提交中可能仍残留早期的凭证。建议不要再使用课程演示用的任何密钥，正式环境一律换新。

**6. 接口返回 `401` / `403`**

- `401`：未登录或 token 无效/过期（`authorization` 头）。access token 有效期较短（`JwtConstants.JWT_TOKEN_TTL`，当前为 5 分钟），需用 `refresh` 头刷新。
- `403`：已登录但角色不匹配。权限规则来自 auth-service 维护的 `auth:privileges` Redis 哈希，网关本地缓存 20 秒刷新一次，权限变更后可能有短暂延迟。

---

## 许可与声明

本项目为教学示例代码，仅供学习研究使用。代码中涉及的第三方服务密钥、商户号等均为演示数据，不构成任何商业授权。
