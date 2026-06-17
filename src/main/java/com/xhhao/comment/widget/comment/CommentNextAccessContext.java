package com.xhhao.comment.widget.comment;

import run.halo.app.infra.AnonymousUserConst;

record CommentNextAccessContext(String username, boolean canViewAll) {

    boolean anonymous() {
        return AnonymousUserConst.isAnonymousUser(username);
    }
}
