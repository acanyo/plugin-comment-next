package com.xhhao.comment.widget.interactionplus;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class InteractionPlusAvailableCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return InteractionPlusAvailability.isClassPresent(context.getClassLoader());
    }
}
