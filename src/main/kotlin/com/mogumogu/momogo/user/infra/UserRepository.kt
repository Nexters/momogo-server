package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>
