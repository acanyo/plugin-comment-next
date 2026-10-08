# ip2region 离线数据说明

本目录下的 `ip2region_v4.xdb` 与 `ip2region_v6.xdb` 来自开源项目
[ip2region](https://github.com/lionsoul2014/ip2region)，遵循 Apache License 2.0。

- 用途：当配置的在线 IP 属地接口超时、限流或返回异常时，插件用这两个数据文件在本地解析评论者 IP 属地（IPv4 / IPv6）。
- 解析结果只用于在评论列表展示「广东」「日本」这类归属地文本，数据不产生任何外部请求。
- 数据文件随插件版本更新，更新节奏跟随 ip2region 上游发布；精确度与更新频率由上游数据源决定。
- 如需更高精确度或更频繁的数据更新，可在插件设置中替换为自建的属地接口，或在 admin 侧调整缓存与超时策略。
