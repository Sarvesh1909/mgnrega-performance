# Fix PostgreSQL Password Authentication Error

## Error: `password authentication failed for user "postgres"`

This means the password in `application.properties` doesn't match your PostgreSQL password.

---

## Solution 1: Update application.properties with Correct Password

### Step 1: Find Your PostgreSQL Password

**Option A: Check if you remember it**
- If you set a password during PostgreSQL installation, use that

**Option B: Use pgAdmin to check**
1. Open pgAdmin 4
2. Connect to your PostgreSQL server
3. Check if you can connect (you know the password if you can)

### Step 2: Update application.properties

1. Open: `backend/src/main/resources/application.properties`

2. Find line 7:
```properties
spring.datasource.password=${DATABASE_PASSWORD:postgres}
```

3. Replace `postgres` with your actual password:
```properties
spring.datasource.password=${DATABASE_PASSWORD:YOUR_ACTUAL_PASSWORD}
```

**Example:**
```properties
spring.datasource.password=${DATABASE_PASSWORD:mypassword123}
```

4. Save the file

5. Try running backend again:
```bash
cd backend
mvn spring-boot:run
```

---

## Solution 2: Reset PostgreSQL Password to "postgres"

### For Windows:

#### Method 1: Using pgAdmin
1. Open pgAdmin 4
2. Right-click on PostgreSQL server → **Properties**
3. Go to **Connection** tab
4. Change password to `postgres`
5. Click **Save**

#### Method 2: Using Command Line (if you know current password)
```bash
# Open Command Prompt as Administrator
# Navigate to PostgreSQL bin folder (usually):
cd "C:\Program Files\PostgreSQL\15\bin"

# Reset password
psql -U postgres
ALTER USER postgres WITH PASSWORD 'postgres';
\q
```

#### Method 3: Using pg_ctl (if you have admin access)
```bash
# Stop PostgreSQL service
net stop postgresql-x64-15

# Start in single-user mode
cd "C:\Program Files\PostgreSQL\15\bin"
pg_ctl -D "C:\Program Files\PostgreSQL\15\data" -o "-c autovacuum=off" start

# In another Command Prompt:
psql -U postgres
ALTER USER postgres WITH PASSWORD 'postgres';
\q

# Stop single-user mode and start normally
pg_ctl -D "C:\Program Files\PostgreSQL\15\data" stop
net start postgresql-x64-15
```

### For Mac/Linux:

```bash
# Switch to postgres user
sudo -u postgres psql

# In PostgreSQL prompt:
ALTER USER postgres WITH PASSWORD 'postgres';

# Exit
\q
```

---

## Solution 3: Use Environment Variable (More Secure)

Instead of hardcoding password, use environment variable:

### Step 1: Set Environment Variable

**Windows (Command Prompt):**
```bash
set DATABASE_PASSWORD=your_password_here
```

**Windows (PowerShell):**
```powershell
$env:DATABASE_PASSWORD="your_password_here"
```

**Mac/Linux:**
```bash
export DATABASE_PASSWORD=your_password_here
```

### Step 2: application.properties already uses it!

The file already has:
```properties
spring.datasource.password=${DATABASE_PASSWORD:postgres}
```

So if `DATABASE_PASSWORD` environment variable is set, it will use that.
Otherwise, it defaults to `postgres`.

---

## Solution 4: Create New PostgreSQL User (Alternative)

If you can't reset the password, create a new user:

### Step 1: Connect to PostgreSQL
```bash
psql -U postgres
# Enter your current password when prompted
```

### Step 2: Create New User
```sql
CREATE USER mgnrega_user WITH PASSWORD 'mgnrega123';
ALTER USER mgnrega_user CREATEDB;
GRANT ALL PRIVILEGES ON DATABASE mgnrega TO mgnrega_user;
\q
```

### Step 3: Update application.properties
```properties
spring.datasource.username=mgnrega_user
spring.datasource.password=${DATABASE_PASSWORD:mgnrega123}
```

---

## Solution 5: Temporary Fix - Disable Database (Testing Only)

**⚠️ WARNING: This disables database features. Only for testing!**

Edit `backend/src/main/resources/application.properties`:

Add at the top:
```properties
app.useDatabase=false
```

And comment out database config:
```properties
#spring.datasource.url=${DATABASE_URL:jdbc:postgresql://localhost:5432/mgnrega}
#spring.datasource.username=${DATABASE_USER:postgres}
#spring.datasource.password=${DATABASE_PASSWORD:postgres}
```

**Note:** You'll lose database persistence features. Use only for testing UI.

---

## Quick Test: Verify PostgreSQL Connection

After fixing password, test connection:

```bash
# Windows (find PostgreSQL bin folder first):
cd "C:\Program Files\PostgreSQL\15\bin"
psql -U postgres -d mgnrega

# Mac/Linux:
psql -U postgres -d mgnrega
```

If it asks for password and connects successfully, password is correct!

---

## Recommended Solution

**Best approach for development:**
1. Use Solution 1 - Update application.properties with correct password
2. Or Solution 3 - Use environment variable

**Best approach for production:**
- Always use environment variables (Solution 3)
- Never hardcode passwords in files

---

## After Fixing

1. Save `application.properties`
2. Restart backend:
```bash
cd backend
mvn spring-boot:run
```

3. You should see:
```
Started BackendApplication in X.XXX seconds
```

No more password errors! ✅



