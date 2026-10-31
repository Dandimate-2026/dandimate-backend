package com.dandimate.durooduroo.common.auth;
import com.dandimate.durooduroo.user.service.KakaoAccountService;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.OAuth2User;
@Service
public class KakaoUserService extends DefaultOAuth2UserService {
    private final KakaoAccountService accounts;
    public KakaoUserService(KakaoAccountService accounts) { this.accounts = accounts; }
    @Override public OAuth2User loadUser(OAuth2UserRequest request) {
        OAuth2User user = super.loadUser(request);
        Object id = user.getAttribute("id");
        if (!"kakao".equals(request.getClientRegistration().getRegistrationId()) || id == null)
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"));
        return new AccountPrincipal(accounts.findOrCreate(id.toString()));
    }
}
