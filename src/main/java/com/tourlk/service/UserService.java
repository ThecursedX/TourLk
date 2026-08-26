package com.tourlk.service;

import com.tourlk.entity.User;

public interface UserService {

    User getById(Long id);

    User getByEmail(String email);

    boolean emailExists(String email);

    User save(User user);

}
