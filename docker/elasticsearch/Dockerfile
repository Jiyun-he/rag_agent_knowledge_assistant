# Elasticsearch + analysis-smartcn 中文分词插件
# 插件版本随镜像 tag 安装，二者必须一致（见 docker-compose.yml 中 build 指向本目录）
FROM docker.elastic.co/elasticsearch/elasticsearch:8.17.4

RUN bin/elasticsearch-plugin install --batch analysis-smartcn
