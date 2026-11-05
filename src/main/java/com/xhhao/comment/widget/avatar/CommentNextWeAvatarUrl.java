package com.xhhao.comment.widget.avatar;

import com.xhhao.comment.widget.identity.CommentNextEmailHash;

public final class CommentNextWeAvatarUrl {

    private static final String BASE_URL = "https://weavatar.com/avatar/";

    private static final String QUERY = "?d=404&f=webp&s=96";

    private CommentNextWeAvatarUrl() {
    }

    public static String forEmail(String email) {
        var hash = CommentNextEmailHash.fromEmail(email);
        return hash == null ? defaultAvatar() : BASE_URL + hash + QUERY;
    }

    public static String defaultAvatar() {
        return "";
    }
}
