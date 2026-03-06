package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.BlogConstants;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.awardtravelupdates.service.GroqAccessor;
import org.springframework.stereotype.Service;

@Service
public class DoCBlogSummaryAgent extends AbstractBlogSummaryAgent {

    public DoCBlogSummaryAgent(GroqAccessor groqAccessor,
                               PostSummaryCacheRepository postSummaryCacheRepository) {
        super(groqAccessor, postSummaryCacheRepository);
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
