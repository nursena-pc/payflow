package com.nursena.payflow.user.adapter.out.security;

interface JwtKeyProvider {

    JwtKeyRing load();
}
