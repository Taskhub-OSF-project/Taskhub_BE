# Supabase database setup

TaskHub can run against Supabase Postgres with the `supabase` Spring profile.

## 1. Create a Supabase project

1. Open Supabase Dashboard.
2. Create or open your project.
3. Go to **Project Settings > Database**.
4. Copy the **Session pooler** or **Direct connection** URI.

For most local/dev runs, the direct URI is fine. For deployed serverless platforms,
prefer the pooler URI.

## 2. Set environment variables

Use the values from Supabase Database settings:

```text
SPRING_PROFILES_ACTIVE=supabase
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<your-database-password>
SPRING_JPA_HIBERNATE_DDL_AUTO=update
```

If you use a Supabase pooler URI, keep the `jdbc:postgresql://` prefix and include
the username exactly as Supabase shows it.

## 3. Run backend

From `Taskhub_BE/BE`:

```powershell
mvn spring-boot:run
```

On first run, Hibernate will create/update the tables in Supabase because
`SPRING_JPA_HIBERNATE_DDL_AUTO=update`.

## 4. Production note

After the schema has been created and verified, set:

```text
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
```

This prevents accidental schema changes when the app starts.
