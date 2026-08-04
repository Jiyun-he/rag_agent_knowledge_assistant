package com.nuaa.ragagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
/**
 * @author jiyunhe
 */

@MapperScan("com.nuaa.ragagent.mapper")
@SpringBootApplication
public class RagAgentKnowledgeAssistantApplication {

	public static void main(String[] args) {
		SpringApplication.run(RagAgentKnowledgeAssistantApplication.class, args);
	}
}
