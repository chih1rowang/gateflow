# deploy 说明

## docker-compose 底座（W1）

Conductor（UI:5000 / API:8080）+ MySQL（宿主机 3307→容器 3306，因本机 3306 已被系统 MySQL 服务占用）+ Redis（6379）。

```bash
cd deploy
docker compose up -d     # 启动全家桶底座
docker compose ps        # 查看状态
docker compose down      # 停止（数据在 named volumes 里，不丢）
```

## 国内网络：Docker Hub 直连不通时

本机实测（2026-10-19）Docker Hub 直连超时。用 daocloud 镜像前缀拉取后打回标准 tag，compose 即可正常使用：

```bash
docker pull docker.m.daocloud.io/conductoross/conductor:next
docker tag  docker.m.daocloud.io/conductoross/conductor:next conductoross/conductor:next

docker pull docker.m.daocloud.io/library/mysql:8.4
docker tag  docker.m.daocloud.io/library/mysql:8.4 mysql:8.4

docker pull docker.m.daocloud.io/library/redis:7-alpine
docker tag  docker.m.daocloud.io/library/redis:7-alpine redis:7-alpine
```

（备选镜像源：`docker.1ms.run`、`dockerproxy.net`；也可在 Docker Desktop → Settings → Docker Engine 里加 `registry-mirrors` 永久生效，需重启 Docker。）

## W4 将扩展为 7 服务

+ Kafka + gateway + worker + panel + nginx（见 docs/design.md，届时补节）。
