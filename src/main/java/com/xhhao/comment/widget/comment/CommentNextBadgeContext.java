package com.xhhao.comment.widget.comment;

import com.xhhao.comment.widget.SettingConfigGetter;

record CommentNextBadgeContext(
    SettingConfigGetter.BadgeConfig settings
) {

    CommentNextBadgeContext {
        settings = settings == null ? SettingConfigGetter.BadgeConfig.empty() : settings;
    }
}
