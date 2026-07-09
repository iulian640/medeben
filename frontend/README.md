# MeDeben — frontend

Vue 3 + Vite + TypeScript. PWA (vite-plugin-pwa); más adelante se empaqueta
para Android con Capacitor (ADR D14).

## Estructura

```
src/
├── views/       # Vistas de página (router)
├── components/  # Componentes reutilizables
├── stores/      # Estado global (Pinia)
├── services/    # Cliente API (fetch hacia /api/v1)
└── router/      # Vue Router
```

## Comandos

```bash
npm install
npm run dev      # http://localhost:5173 — proxy /api → localhost:8080
npm run build    # type-check (vue-tsc) + build de producción
npm run preview
```

En dev, las llamadas a `/api` se redirigen al backend Spring Boot local
(arráncalo antes: ver [backend/README.md](../backend/README.md)).

Los iconos PWA (`public/pwa-*.png`) son placeholders — pendiente diseño real.

Ver [docs/dev-setup.es.md](../docs/dev-setup.es.md) para el setup completo.
