package org.alfresco.contentlake.rag;

import org.alfresco.contentlake.service.ContentLakeNodeStatusService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication(scanBasePackages = "org.alfresco.contentlake")
@ComponentScan(
        basePackages = "org.alfresco.contentlake",
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ContentLakeNodeStatusService.class)
)
public class RagServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagServiceApplication.class, args);
    }
}
