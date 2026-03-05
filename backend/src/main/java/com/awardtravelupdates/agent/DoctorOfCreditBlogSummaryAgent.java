package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.BlogConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class DoctorOfCreditBlogSummaryAgent extends AbstractBlogSummaryAgent {

    public DoctorOfCreditBlogSummaryAgent(WebClient groqClient, ObjectMapper objectMapper) {
        super(groqClient, objectMapper);
    }

    @Override
    public String getBlogId() {
        return BlogConstants.BLOG_DOCTOR_OF_CREDIT;
    }

    @Override
    public String getDisplayName() {
        return "Doctor of Credit";
    }
}
