package com.mogumogu.momogo.photo.infra

import com.mogumogu.momogo.photo.domain.PhotoGroup
import org.springframework.data.jpa.repository.JpaRepository

interface PhotoGroupRepository : JpaRepository<PhotoGroup, Long>
