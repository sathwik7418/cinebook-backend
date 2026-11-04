package com.cinebook.chat.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Enables database-backed CineChat only when both its feature and shared datasource are enabled. */
public final class CineChatPersistenceEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return context.getEnvironment().getProperty("cinebook.chat.enabled", Boolean.class, false)
                && context.getEnvironment().getProperty("cinebook.social.database.enabled", Boolean.class, false);
    }
}
