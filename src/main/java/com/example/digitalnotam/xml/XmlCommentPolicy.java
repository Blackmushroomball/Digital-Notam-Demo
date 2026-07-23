package com.example.digitalnotam.xml;

import org.w3c.dom.*;
import java.util.*;

/**
 * Keeps scenario comments attached to the XML node they describe.
 *
 * A blueprint comment is not data. When its following data node is replaced,
 * the comment must be removed and recreated together with the replacement.
 */
public final class XmlCommentPolicy {
    private XmlCommentPolicy(){}

    public static void clearDirectComments(Element parent){
        for(Node node=parent.getFirstChild();node!=null;){
            Node next=node.getNextSibling();
            if(node.getNodeType()==Node.COMMENT_NODE)parent.removeChild(node);
            node=next;
        }
    }

    public static Comment insertBefore(Element target,String text){
        Node parent=target.getParentNode();
        if(parent==null)throw new IllegalArgumentException("Cannot attach a comment to a detached XML element");
        Comment comment=target.getOwnerDocument().createComment(" "+text.trim()+" ");
        parent.insertBefore(comment,target);
        return comment;
    }

    public static void validate(Document document){
        validateNode(document.getDocumentElement());
    }

    private static void validateNode(Element parent){
        Comment pending=null;
        for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling()){
            if(node.getNodeType()==Node.TEXT_NODE&&node.getNodeValue().isBlank())continue;
            if(node instanceof Comment comment){
                if(pending!=null)throw new IllegalArgumentException("Adjacent XML comments are not allowed inside "+parent.getLocalName()+": "+pending.getNodeValue().trim()+" / "+comment.getNodeValue().trim());
                pending=comment;continue;
            }
            if(node instanceof Element element){
                if(pending!=null)validateTarget(pending,element);
                pending=null;validateNode(element);
            }else pending=null;
        }
        if(pending!=null)throw new IllegalArgumentException("Orphan XML comment at the end of "+parent.getLocalName()+": "+pending.getNodeValue().trim());
    }

    private static void validateTarget(Comment comment,Element target){
        String text=comment.getNodeValue().trim().toLowerCase(Locale.ROOT);
        String local=target.getLocalName();
        if(text.contains("activation status of the airspace")&&!"activation".equals(local))
            throw new IllegalArgumentException("Airspace activation comment must immediately precede aixm:activation");
        if(text.contains("link to the event")&&!"extension".equals(local))
            throw new IllegalArgumentException("Event-link comment must immediately precede aixm:extension");
        if(text.matches("[a-z0-9]+\\s+.+")&&target.getParentNode() instanceof Element parent&&"AIXMBasicMessage".equals(parent.getLocalName())&&!"hasMember".equals(local))
            throw new IllegalArgumentException("Feature label comment must immediately precede message:hasMember");
    }
}
