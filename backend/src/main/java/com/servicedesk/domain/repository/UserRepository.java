package com.servicedesk.domain.repository;

import com.servicedesk.domain.user.User;
import com.servicedesk.domain.user.UserId;
import java.util.List;
import java.util.Optional;

/** Domain port for user queries and persistence. */
public interface UserRepository {

  Optional<User> findById(UserId id);

  Optional<User> findByUsername(String username);

  List<User> findTechnicians();

  List<User> findAll();

  User save(User user);
}
