package com.xhhao.comment.widget.iplocation;

import run.halo.app.extension.MetadataOperator;

public final class CommentNextIpLocationAnnotation {

    public static final String NAME = "commentnext.xhhao.com/ip-location";

    private CommentNextIpLocationAnnotation() {
    }

    public static String read(MetadataOperator metadata) {
        var annotations = metadata == null ? null : metadata.getAnnotations();
        return annotations == null ? "" : CommentNextIpLocationText.displayLabel(annotations.get(NAME));
    }
}
