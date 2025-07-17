package org.ipan.hedging.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.ipan.hedging.domain.model.AppUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Implementation of UserDetails for Spring Security.
 */
@AllArgsConstructor
@Getter
public class UserDetailsImpl implements UserDetails {
    private Long id;
    @JsonIgnore
    private String password;
    
    public static UserDetailsImpl build(AppUser user) {
        return new UserDetailsImpl(user.getTelegramId(), user.getDashboardPwd());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getUsername() {
        return getId().toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}