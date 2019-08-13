package com.iflytek.tps.foun.authority;


public final class AuthorityContext {
    private AuthoritySession authoritySession;

    private static ThreadLocal<AuthorityContext> holder = ThreadLocal.withInitial(() -> new AuthorityContext());

    public static AuthorityContext get(){
        return holder.get();
    }

    public AuthoritySession getAuthoritySession() {
        return authoritySession;
    }

    public void setAuthoritySession(AuthoritySession authoritySession) {
        this.authoritySession = authoritySession;
    }

    public void clear() {
        holder.remove();
    }
}
