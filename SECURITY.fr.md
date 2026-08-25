# Politique de sécurité

## Périmètre

Ce dépôt contient des providers SPI Keycloak — authentificateurs, écouteurs d'événements, fédération — destinés à être compilés et déployés dans un vrai serveur Keycloak. Une faille ici peut affecter l'authentification de tout serveur exécutant ces providers ; les signalements sur ce code sont traités sérieusement.

## Versions supportées

Seule la branche `main` est maintenue. Vérifiez la version de Keycloak déclarée dans `pom.xml` avant de signaler : un problème corrigé en amont dans un Keycloak plus récent peut encore être présent ici.

## Signaler une vulnérabilité

**N'ouvrez pas d'issue publique et n'envoyez pas d'e-mail.** Une issue publique informe
tout le monde du problème avant qu'un correctif existe.

Passez par les GitHub Security Advisories, qui gardent le signalement privé jusqu'à ce
qu'un correctif soit prêt :

1. Ouvrez l'onglet **Security** de ce dépôt.
2. Allez dans **Advisories**.
3. Cliquez sur **Report a vulnerability**.

Un signalement utile dit quelle est la faille, comment la reproduire, et ce qu'un
attaquant en tire. Une preuve de concept aide plus qu'une description.

## À quoi s'attendre

Ce dépôt est maintenu par une seule personne, donc calibrez les délais en conséquence :
un accusé de réception sous une semaine, et une réponse honnête sur le fait que le
problème sera corrigé ou non, plutôt que du silence. Si un correctif est retenu, la
divulgation est coordonnée avec vous.

Si un signalement s'avère ne pas être une vulnérabilité, on vous dira pourquoi.

---

<div align="center">
  <img src="assets/brand/jihedailabs-logo.svg" alt="JihedAiLabs" width="120"/>
  <br/>
  <sub>Un projet <a href="https://github.com/jihedbfr-art"><b>JihedAiLabs</b></a></sub>
  <br/>
  <sub><a href="./SECURITY.md">English version</a></sub>
</div>
