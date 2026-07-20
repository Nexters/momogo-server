package com.mogumogu.momogo.infrastructure.database.repository

import com.mogumogu.momogo.infrastructure.database.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, Long>
