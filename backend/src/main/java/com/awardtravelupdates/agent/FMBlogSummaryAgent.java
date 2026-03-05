package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.BlogConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class FMBlogSummaryAgent extends AbstractBlogSummaryAgent {

    public FMBlogSummaryAgent(RestClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    @Override
    public String getBlogId() {
        return BlogConstants.BLOG_FREQUENT_MILER;
    }

    @Override
    public String getDisplayName() {
        return "Frequent Miler";
    }
}
