package com.nuaa.ragagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
/**
 * 开启 Spring 定时任务调度（周期索引对账使用）。
 *
 * @author jiyunhe
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
