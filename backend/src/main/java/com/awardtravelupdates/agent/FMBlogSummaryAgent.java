package com.awardtravelupdates.agent;

import com.awardtravelupdates.constants.BlogConstants;
import com.awardtravelupdates.repository.PostSummaryCacheRepository;
import com.awardtravelupdates.service.GroqAccessor;
import org.springframework.stereotype.Service;

@Service
public class FMBlogSummaryAgent extends AbstractBlogSummaryAgent {

    public FMBlogSummaryAgent(GroqAccessor groqAccessor,
                              PostSummaryCacheRepository postSummaryCacheRepository) {
        super(groqAccessor, postSummaryCacheRepository);
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
