package com.xhhao.comment.widget.comment;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.interactionplus.AuthorIdentityLoader;

record CommentNextBadgeContext(
    SettingConfigGetter.BadgeConfig settings,
    AuthorIdentityLoader identityLoader
) {

    CommentNextBadgeContext {
        settings = settings == null ? SettingConfigGetter.BadgeConfig.empty() : settings;
        identityLoader = identityLoader == null ? AuthorIdentityLoader.disabled() : identityLoader;
    }
}
