![https://search.maven.org/artifact/org.griesbacher.jocose/jocose_javaagent/](https://img.shields.io/maven-central/v/org.griesbacher.jocose/jocose_javaagent.svg)

JOCOSE - *J*mx *O*ver *CO*n*S*ul *E*xporter
======
This exporter uses the original jmx_exporter and combines it with consul.
It's aim is to collect JMX metrics in distributed systems, in a automated way.

## Consul Start
```` bash
consul agent \
 -server \
 -data-dir=/tmp/consul \
 -client=0.0.0.0 \
 -datacenter=Master \
 -bootstrap-expect=1 \
 -ui
````

## Prometheus Configuration
``` yaml
global:
  scrape_interval:     2s # Set the scrape interval to every 15 seconds. Default is every 1 minute.
  evaluation_interval: 2s # Evaluate rules every 15 seconds. The default is every 1 minute.

rule_files:

scrape_configs:
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
  - job_name: consul
    consul_sd_configs:
     - server: 'localhost:8500'
    relabel_configs:
      - source_labels: ['__address__']
        separator:     ':'
        regex:         '(.*):(8300)'
        action:        drop
      - source_labels: [__meta_consul_service]
        regex: (.*)
        replacement: '${1}'
        target_label: service
      - source_labels: [__meta_consul_tags]
        regex: .*,job_([^,]+),.*
        replacement: '${1}'
        target_label: job
      - source_labels: [__meta_consul_tags]
        regex: .*,user_([^,]+),.*
        replacement: '${1}'
        target_label: user
```

## Example Instrumentation
- YARN
    
    $YARN_HOME/bin/yarn:
    - ResourceManager
    
        ~L: 238
        ``` bash
        YARN_RESOURCEMANAGER_OPTS="$YARN_RESOURCEMANAGER_OPTS -javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sResourceManager,-cfile:///usr/lib/prometheus/jocose.yml"
        ```
    - NodeManager
    
        ~L: 268
        ``` bash
        YARN_NODEMANAGER_OPTS="$YARN_NODEMANAGER_OPTS -javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sNodeManager,-cfile:///usr/lib/prometheus/jocose.yml"
        ```
    - ApplicationMaster / Map / Reduce Jobs
        
        $YARN_HOME/etc/hadoop/mapred-site.xml:
        ```xml
        <?xml version="1.0" encoding="UTF-8"?>
        <?xml-stylesheet type="text/xsl" href="configuration.xsl"?>
        <configuration>
                <property>
                        <name>mapreduce.framework.name</name>
                        <value>yarn</value>
                </property>
                <property>
                        <name>mapreduce.map.java.opts</name>
                        <value>-javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sMap,-cfile:///usr/lib/prometheus/jocose.yml</value>
                </property>
                <property>
                        <name>mapreduce.reduce.java.opts</name>
                        <value>-javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sReduce,-cfile:///usr/lib/prometheus/jocose.yml</value>
                </property>
                <property>
                        <name>yarn.app.mapreduce.am.command-opts</name>
                        <value>-javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sAppMaster,-cfile:///usr/lib/prometheus/jocose.yml</value>
                </property>
        </configuration>
        ```
- HDFS

    $HADOOP_HDFS_HOME/bin/hdfs:
    - NameNode
    
        ~L: 136
        ```bash
        HADOOP_NAMENODE_OPTS="$HADOOP_NAMENODE_OPTS -javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sNameNode,-cfile:///usr/lib/prometheus/jocose.yml"
        ```
    - SecondaryNameNode
    
        ~L: 141
        ```bash
        HADOOP_SECONDARYNAMENODE_OPTS="$HADOOP_SECONDARYNAMENODE_OPTS -javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sSecondaryNameNode,-cfile:///usr/lib/prometheus/jocose.yml"
        ```
    - DataNode
    
        ~L: 145
        ```bash
        HADOOP_DATANODE_OPTS="$HADOOP_DATANODE_OPTS -javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-sDataNode,-cfile:///usr/lib/prometheus/jocose.yml"
        ```
- Spark
    
    $SPARK_HOME/bin/spark-class:  
    ~L: 98
    ``` bash
        NAME="Spark"
        for i in "${CMD[@]}"
        do
          if [[ $i == *"org.apache."* ]]; then
            NAME="Spark-"`echo $i | rev | cut -d "." -f 1 | rev`
          fi
        done
        
        AGENT="-javaagent:/usr/lib/prometheus/jocose_javaagent-1.0-SNAPSHOT.jar=-H0.0.0.0,-s$NAME,-cfile:///usr/lib/prometheus/jocose.yml"
        TMP_CMD=(${CMD[0]} $AGENT ${CMD[@]:1:$((${#CMD[@]}-1))});
        CMD=("${TMP_CMD[@]}")
    ```