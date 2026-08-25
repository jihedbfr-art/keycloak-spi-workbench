# keycloak-spi-workbench

[![CI](https://github.com/jihedbfr-art/keycloak-spi-workbench/actions/workflows/ci.yml/badge.svg)](https://github.com/jihedbfr-art/keycloak-spi-workbench/actions)

[English version](./README.md)

Des providers SPI Keycloak sur mesure, construits un par un, chacun avec de vrais tests. La plupart
des exemples publics de SPI Keycloak s'arrêtent à « voici une classe qui implémente l'interface » —
la vraie difficulté, c'est de prouver que le composant se déploie proprement sur un serveur réel et
qu'il se comporte correctement une fois en place. Cette partie-là manque presque toujours.

**Vérifier en une commande :** `mvn clean verify` construit chaque provider et lance la suite
Testcontainers — un vrai Keycloak, un vrai broker Kafka, un vrai Postgres — pour prouver que
chacun se déploie et se comporte réellement, pas juste qu'il compile. Aucune installation requise
en dehors de Docker.

## Ce qu'il y a ici : un authenticator conditionnel basé sur le risque

`conditional-risk-based` conditionne une étape du flux de connexion (typiquement l'OTP) à une règle
métier plutôt qu'à un oui/non identique pour tout le monde, à chaque fois :

1. un attribut de dérogation par utilisateur force la décision pour un compte donné (cas support :
   « forcer le MFA sur celui-ci »), en court-circuitant tout le reste
2. le client sur lequel porte l'authentification figure dans une liste configurée de clients à
   risque — une console d'administration interne doit toujours exiger l'étape supplémentaire, un
   site vitrine public jamais
3. l'attribut de niveau de risque de l'utilisateur correspond à une valeur configurée comme risquée
4. sinon, la condition ne matche pas et le flux continue sans étape supplémentaire

C'est exactement le type de règle dont a besoin un contrôle d'accès réaliste façon BSS/télécom —
« ce compte touche aux dérogations de facturation, toujours le challenger » ou « ce client est la
console d'exploitation, toujours exiger l'étape supplémentaire » — plutôt que le MFA binaire
pour-tout-le-monde-ou-personne que montrent la plupart des tutoriels.

## Ce qu'il y a ici : un event listener Kafka

`kafka-event-publisher` transfère les événements `LOGIN`, `LOGIN_ERROR` et `LOGOUT` vers un topic
Kafka — pour les systèmes en aval qui ont besoin de savoir si une connexion a réussi, pas pour le
journal d'événements propre à Keycloak (il en a déjà un). Les événements admin ne sont
délibérément pas transférés ; c'est le problème d'un autre consommateur, et mélanger les deux
formes dans un seul topic ne fait que reporter le travail de filtrage sur tous ceux qui le lisent.
Configurable via la config du provider : `bootstrapServers`, `topic`, `eventTypes` (séparés par des
virgules, par défaut login/login-error/logout). Une panne Kafka ne bloque jamais une connexion — le
producer échoue vite (`max.block.ms=2000`) et un envoi raté est journalisé, pas levé en exception.

Le jar est shaded — `kafka-clients` et `jackson-databind` sont embarqués, puisque le répertoire
`providers/` de Keycloak ne résout pas les dépendances à votre place.

## Ce qu'il y a ici : un provider de stockage utilisateur legacy

`legacy-user-storage` fédère des utilisateurs en lecture seule depuis une table JDBC existante, au
lieu d'imposer une migration big-bang vers le store propre de Keycloak. C'est la forme dont a
besoin une vraie migration : le système legacy (une table d'abonnés BSS, un vieux CRM, peu importe)
reste la source de vérité pour les comptes déjà existants, les nouveaux comptes vont directement
dans Keycloak, et personne n'a à écrire de logique de synchronisation dans les deux sens. Prend en
charge la recherche par nom d'utilisateur/email/id, la recherche depuis la console admin, et la
validation du mot de passe contre le hash legacy — en lecture seule, aucune écriture ne revient
jamais vers la table legacy. Les noms de table et de colonnes sont configurables ; l'algorithme de
hash est branchable via `LegacyPasswordHasher` (celui par défaut, SHA-256 de sel+mot de passe, est
un placeholder — remplacez-le par ce qu'utilisait réellement votre système legacy avant de pointer
ceci vers des données réelles).

Du JDBC brut, pas d'ORM, pas de pool de connexions — le fournisseur de connexion est le point
d'extension où un vrai déploiement brancherait plutôt une `DataSource` poolée. Monitoring Sentry
optionnel pour les erreurs et les performances : définissez `SENTRY_DSN` et chaque appel JDBC
obtient un span, les `SQLException` sont remontées. Sans DSN, sans Sentry — rien ne change.

## Installation

Construisez le jar du provider et déposez-le dans le répertoire `providers/` de Keycloak :

```bash
git clone https://github.com/jihedbfr-art/keycloak-spi-workbench.git
cd keycloak-spi-workbench
mvn clean package
cp target/keycloak-spi-workbench-0.4.0.jar $KEYCLOAK_HOME/providers/
```

Il vous faudra aussi le driver JDBC de votre base legacy (par exemple `postgresql-42.7.3.jar`)
posé dans `providers/` à côté, puis :

```bash
$KEYCLOAK_HOME/bin/kc.sh build
```

Pour l'authenticator conditionnel : dans un flux d'authentification, ajoutez l'exécution
« Condition - Risk-Based Step-Up », réglez-la sur `REQUIRED`, configurez les IDs de clients à
risque / l'attribut de niveau de risque / l'attribut de dérogation selon vos besoins, et réglez
l'exécution OTP juste après sur `CONDITIONAL`.

Pour le publisher Kafka : ajoutez `kafka-event-publisher` aux event listeners du realm (Realm
settings → Events → Event listeners), puis configurez-le au démarrage, par exemple :

```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  --spi-events-listener-kafka-event-publisher-bootstrap-servers=kafka:9092 \
  --spi-events-listener-kafka-event-publisher-topic=auth-events
```

Pour le provider de stockage utilisateur legacy : Realm settings → User federation → Add provider
→ `legacy-user-storage`, puis renseignez l'URL JDBC, l'utilisateur, le mot de passe et le nom de
table. La table doit avoir les colonnes `id`, `username`, `email`, `first_name`, `last_name`,
`password_hash`, `password_salt`, `enabled` (figées pour l'instant — des noms de colonnes
configurables sont une évolution naturelle pour la v0.4).

## Tests

```bash
mvn test              # tests unitaires — aucun Docker requis
mvn verify             # lance aussi les vérifications Testcontainers : vrai Keycloak, vrai Kafka, vrai Postgres
```

`RiskBasedConditionalAuthenticatorTest` couvre directement l'ordre d'évaluation en quatre étapes
avec des objets modèle Keycloak mockés. `KafkaEventListenerProviderTest` / `...FactoryTest`
couvrent le filtrage des événements, la forme du payload JSON et le parsing de configuration.
`LegacyUserRepositoryTest` exécute du vrai SQL contre une base H2 en mémoire (rapide, sans Docker)
et `LegacyUserStorageProviderTest` couvre la logique de validation d'identifiants et de délégation
de recherche avec un repository simulé. Trois tests d'intégration tournent à la phase `verify` :
l'un démarre un vrai conteneur `quay.io/keycloak/keycloak` avec le jar construit déployé et
confirme que l'authenticator conditionnel s'enregistre via l'API REST d'administration ; un autre
démarre un vrai broker Kafka et prouve qu'un message fait l'aller-retour via un vrai producer et un
vrai consumer ; le dernier rejoue les requêtes du stockage legacy contre un vrai conteneur
PostgreSQL, car le dialecte H2 ne correspond pas toujours exactement à celui de Postgres (le `LIKE`
insensible à la casse et les colonnes booléennes étant les deux différences qui comptent en
pratique).

## Générer un nouveau provider

Le répertoire [`archetype/`](archetype) contient un archetype Maven qui génère un nouveau module
SPI `Authenticator` dans la même forme — authenticator et factory de base, enregistrement
`META-INF/services`, un test unitaire, et un IT de déploiement Testcontainers — plutôt que de
copier l'un des trois providers ci-dessus à la main en renommant les éléments. Voir
[archetype/README.md](archetype/README.md) pour la commande de génération. C'est un build
autonome, qui ne fait pas partie du reactor `mvn` de ce projet.

## Roadmap

- Couverture de l'archetype pour les formes event-listener et user-storage, si celui de
  l'authenticator fait ses preuves
- Noms de colonnes configurables pour `legacy-user-storage` (actuellement figés, voir
  Installation ci-dessus)

## Licence

MIT — voir [LICENSE](LICENSE).
