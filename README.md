# SmartLife · 本地生活服务平台

用户、商家、平台三端一体的本地生活服务系统，覆盖外卖点单、优惠券与限时秒杀、
订单履约、内容社区与平台治理。

后端 Maven 多模块（common / pojo / server），配套 138 个单元测试。

## 技术栈

Java 17 · Spring Boot 3.2 · MyBatis-Plus · MySQL 8 · Redis 7 · RabbitMQ 3.12 · WebSocket

## 技术要点

**秒杀链路** —— Redis Lua 把库存校验、时间窗判断、一人一单与扣减合并成单个原子脚本，
通过校验的请求经 RabbitMQ 异步落库，把数据库事务移出接口响应路径。生产端开
publisher confirm，确认失败即回补预扣名额；消费端手动 ACK，幂等由 orderId 判重加
唯一索引兜底。

**缓存治理** —— 按数据特性分方案：店铺详情用逻辑过期，后台异步重建、用户线程零等待；
分类列表用互斥锁。穿透用空值缓存加参数校验，雪崩用 TTL 随机偏移。

**认证** —— JWT 加 Redis 会话双层。JWT 只负责签名，会话活性由 Redis 白名单裁定，
30 分钟 TTL 加阈值续期；踢全端用会话版本号 INCR 原子完成；身份字段一律取 Redis 会话
而非 JWT，密钥泄漏也无法伪造角色提权。

**订单超时取消** —— RabbitMQ 延迟队列（队列级 TTL 加死信转发）替代定时扫表，
延迟消息在事务 afterCommit 才发送，回滚不发；保留每分钟扫表兜底，
两条路径靠 CAS 比对前驱状态，不会重复取消。

## 压测

JMeter 对照 Redisson 分布式锁同步方案，100 / 500 / 1200 三档真并发各压测三轮，
18 轮账目全部一致、零超卖；异步方案 P50 稳定快约 1.7 倍。

## 运行

依赖本机已装 MySQL 8、Redis 7、RabbitMQ 3.12。

```bash
# 1. 建库并导入表结构
mysql -uroot -p < sql/init.sql
mysql -uroot -p < sql/admin-seed.sql     # 管理员账号，可选

# 2. 配置连接信息，二选一：
#    a) 复制 application-local.example.yml 为 application-local.yml，填本机值
#    b) 用环境变量覆盖，见 application.yml 里的 ${SMARTLIFE_*} 占位符

# 3. 构建并启动
cd smartlife-backend
mvn -DskipTests package
java -jar smartlife-server/target/smartlife-server.jar
```

默认端口 8086，接口文档 <http://localhost:8086/doc.html>

## 测试

```bash
cd smartlife-backend && mvn test
```
