import KeycloakProvider from 'next-auth/providers/keycloak'
import {NuxtAuthHandler} from "#auth";

// import { NuxtAuthHandler } from '#auth'

export default NuxtAuthHandler({
    secret: process.env.AUTH_SECRET || 'my-auth-secret',
    // TODO: ADD YOUR OWN AUTHENTICATION PROVIDER HERE, READ THE DOCS FOR MORE: https://sidebase.io/nuxt-auth
    providers: [
        KeycloakProvider.default({
            clientId: "waltid_backend",
            clientSecret: "5FXJ9IxtMTHWfGUDDU8LGZXaWEu3Qqnk",
            //authorization: process.env.AUTH_AUTHORIZATION ?? "",
            issuer: "http://localhost:8080/realms/waltid-keycloak-nuxt",
            idToken: true,
            wellKnown: "http://localhost:8080/realms/waltid-keycloak-nuxt/.well-known/openid-configuration",
             // requestTokenUrl: "http://localhost:8080/auth/realms/waltid-keycloak-nuxt/protocol/openid-connect/auth",
             // accessTokenUrl: "http://localhost:8080/auth/realms/waltid-keycloak-nuxt/protocol/openid-connect/token",
             //  profileUrl: "http://localhost:8080/auth/realms/waltid-keycloak-nuxt/protocol/openid-connect/userinfo",
            authorization: 'http://localhost:8080/realms/waltid-keycloak-nuxt/protocol/openid-connect/auth',

        })
    ],
    callbacks: {
        async session({ session, token, user }) {
            // @ts-ignore
            session.user.id = token.id;
            // @ts-ignore
            session.accessToken = token.accessToken;
            // @ts-ignore
            session.refreshToken = token.refreshToken; // Add this line to store the refresh token in the session

            return session;
        },
        async jwt({ token, user, account, profile, isNewUser }) {
            if (user) {
                token.id = user.id;
                // @ts-ignore

            }
            if (account) {
                token.accessToken = account.access_token;
                token.refreshToken = account.refresh_token; // Add this line to store the refresh token in the JWT

            }
            return token;
        },
    },
})

