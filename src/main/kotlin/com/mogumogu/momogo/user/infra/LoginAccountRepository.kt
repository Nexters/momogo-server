package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.LoginAccount
import org.springframework.data.jpa.repository.JpaRepository

interface LoginAccountRepository : JpaRepository<LoginAccount, Long>
