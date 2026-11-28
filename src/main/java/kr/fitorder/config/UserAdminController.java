package kr.fitorder.config;

import java.util.List;
import kr.fitorder.config.UserAdminService.Create;
import kr.fitorder.config.UserAdminService.Update;
import kr.fitorder.config.UserAdminService.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 계정 관리(관리자). */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final UserAdminService users;

    public UserAdminController(UserAdminService users) {
        this.users = users;
    }

    @GetMapping
    public List<UserView> list() {
        return users.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserView create(@RequestBody Create request, Authentication auth) {
        return users.create(request, auth.getName());
    }

    @PutMapping("/{username}")
    public UserView update(@PathVariable String username, @RequestBody Update update, Authentication auth) {
        return users.update(username, update, auth.getName());
    }
}
