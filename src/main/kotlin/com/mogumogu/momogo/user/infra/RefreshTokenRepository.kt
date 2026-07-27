package com.mogumogu.momogo.user.infra

import com.mogumogu.momogo.user.domain.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long>
