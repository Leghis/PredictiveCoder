# PredictiveCoder

PredictiveCoder est un plugin IntelliJ IDEA avancé qui utilise GPT-4o pour fournir des suggestions de code intelligentes et contextuelles en temps réel.

## Caractéristiques Principales

- 🤖 Suggestions de code IA alimentées par GPT-4o
- ⚡ Complétion en temps réel avec mise en cache
- 🔄 Gestion intelligente du contexte
- 📝 Support multi-lignes avec indentation automatique
- 🎯 Acceptation partielle ou complète des suggestions
- 🔧 Configuration flexible et persistante
- 📊 Interface utilisateur intuitive avec barre d'état

## Prérequis Techniques

- IntelliJ IDEA 2023.2.5+
- Java 17+
- Gradle 7.x+
- Clé API OpenAI valide

## Installation

1. Cloner le repository :
```bash
git clone https://github.com/Leghis/PredictiveCoder.git
```

2. Configuration de l'API :
   - Via l'interface : Tools → Configure OpenAI API Key
   - Via fichier : `~/.predictivecoder/config`
   - Via variable d'environnement : `OPENAI_API_KEY`

3. Build et installation :
```bash
./gradlew buildPlugin
```

## Utilisation

### Raccourcis Clavier

- `Tab` : Accepter la suggestion complète
- `→` (Flèche droite) : Accepter une ligne
- `Cmd/Ctrl + Shift + P` : Activer/désactiver le plugin

### Fonctionnalités Avancées

1. **Suggestions Contextuelles**
   - Analyse du contexte de code environnant
   - Prise en compte de l'indentation
   - Support des structures de contrôle

2. **Gestion Intelligente**
   - Mise en cache des suggestions
   - Debouncing des requêtes
   - Traitement asynchrone

3. **Interface Utilisateur**
   - Barre d'état avec statut
   - Fenêtre d'outils dédiée
   - Notifications intégrées

## Architecture

```
src/main/kotlin/com/predictivecoder/ayinamaerik/
├── actions/          # Actions utilisateur
├── config/          # Configuration et persistance
├── listeners/       # Écouteurs d'événements
├── services/        # Services principaux
├── settings/       # Paramètres du plugin
└── ui/             # Composants d'interface
```

### Composants Clés

- `OpenAIService` : Communication avec l'API GPT-4o
- `SuggestionService` : Gestion des suggestions
- `EditorListener` : Surveillance des modifications
- `BackgroundTaskService` : Traitement asynchrone
- `IndentationService` : Gestion de l'indentation

## Développement

### Build et Tests

```bash
# Build complet
./gradlew build

# Tests unitaires
./gradlew test

# Lancement en mode développement
./gradlew runIde
```

### Configuration Gradle

- Kotlin 1.9.22
- OkHttp 4.11.0
- Coroutines 1.7.3
- Support Java 17

## Contribution

1. Fork le projet
2. Créer une branche (`git checkout -b feature/nouvelle-fonctionnalite`)
3. Commit (`git commit -m 'Ajout nouvelle fonctionnalité'`)
4. Push (`git push origin feature/nouvelle-fonctionnalite`)
5. Créer une Pull Request

## Dépannage

1. **Problèmes de connexion API**
   - Vérifier la clé API
   - Consulter les logs dans idea.log

2. **Performances**
   - Ajuster le délai de suggestion
   - Vérifier l'utilisation mémoire

## Support et Contact

- **Auteur** : Ayina Maerik
- **Email** : ayinamaerik@gmail.com
- **Portfolio** : https://maerik-online-cv.vercel.app/
- **LinkedIn** : https://www.linkedin.com/in/ayinamaerik/

## Licence

Ce projet est sous licence MIT. Voir le fichier [LICENSE](LICENSE) pour plus de détails.

---

⭐ Si ce projet vous est utile, pensez à lui donner une étoile sur GitHub !