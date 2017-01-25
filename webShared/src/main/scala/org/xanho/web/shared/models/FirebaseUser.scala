package org.xanho.web.shared.models

case class FirebaseUser(uid: String,
                        email: String,
                        emailVerified: Boolean,
                        providerId: Option[String],
                        photoUrl: Option[String])
