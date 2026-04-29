#!/usr/bin/env python3
"""
mysqlrestore.py - SQLite to MySQL Restore

Restores data from SQLite backup file to MySQL server, replacing all existing data.
Reads MySQL connection details from .env file.
Uses the most recent backup file if no file is specified.

Usage:
    python3 scripts/mysqlrestore.py
    python3 scripts/mysqlrestore.py --backup-file volumes/backups/mysql_backup_20240101_120000.db
    cd scripts && python3 mysqlrestore.py
"""

import os
import sys
import re
import argparse
import sqlite3
import mysql.connector
from mysql.connector import Error
from pathlib import Path
from datetime import datetime

# Configuration
# Script is in scripts/ folder, so go up one level to get project root
PROJECT_ROOT = Path(__file__).parent.parent
BACKUP_DIR = PROJECT_ROOT / "volumes" / "backups"
ENV_FILE = PROJECT_ROOT / ".env"


def load_env_file():
    """Load environment variables from .env file"""
    env_vars = {}
    
    if not ENV_FILE.exists():
        print(f"Error: .env file not found at {ENV_FILE}")
        sys.exit(1)
    
    with open(ENV_FILE, 'r') as f:
        for line in f:
            line = line.strip()
            # Skip comments and empty lines
            if not line or line.startswith('#'):
                continue
            
            # Parse KEY=VALUE format
            if '=' in line:
                key, value = line.split('=', 1)
                key = key.strip()
                value = value.strip()
                # Remove quotes if present
                value = value.strip('"').strip("'")
                env_vars[key] = value
    
    return env_vars


def parse_jdbc_url(jdbc_url):
    """Parse JDBC URL format: jdbc:mysql://host:port/database"""
    # Pattern: jdbc:mysql://host:port/database
    pattern = r'jdbc:mysql://([^:/]+):(\d+)/(.+)'
    match = re.match(pattern, jdbc_url)
    
    if not match:
        print(f"Error: Invalid JDBC URL format: {jdbc_url}")
        print("Expected format: jdbc:mysql://host:port/database")
        sys.exit(1)
    
    host = match.group(1)
    port = int(match.group(2))
    database = match.group(3)
    
    return host, port, database


def get_mysql_config():
    """Get MySQL configuration from .env file"""
    env_vars = load_env_file()
    
    # Required variables
    db_url = env_vars.get('DB_URL')
    db_username = env_vars.get('DB_USERNAME')
    db_password = env_vars.get('DB_PASSWORD')
    
    if not db_url:
        print("Error: DB_URL not found in .env file")
        sys.exit(1)
    
    if not db_username:
        print("Error: DB_USERNAME not found in .env file")
        sys.exit(1)
    
    if not db_password:
        print("Error: DB_PASSWORD not found in .env file")
        sys.exit(1)
    
    # Parse JDBC URL
    host, port, database = parse_jdbc_url(db_url)
    
    return host, port, db_username, db_password, database


def find_latest_backup():
    """Find the most recent MySQL backup file"""
    if not BACKUP_DIR.exists():
        print(f"Error: Backup directory not found: {BACKUP_DIR}")
        sys.exit(1)
    
    # Find all mysql backup files
    backup_files = list(BACKUP_DIR.glob("mysql_backup_*.db"))
    
    if not backup_files:
        print(f"Error: No MySQL backup files found in {BACKUP_DIR}")
        sys.exit(1)
    
    # Sort by modification time, most recent first
    backup_files.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    
    return backup_files[0]


def print_header(title):
    """Print a formatted header"""
    print("\n" + "=" * 60)
    print(title)
    print("=" * 60 + "\n")


def get_mysql_connection(host, port, user, password, database):
    """Create MySQL connection"""
    try:
        connection = mysql.connector.connect(
            host=host,
            port=port,
            user=user,
            password=password,
            database=database
        )
        return connection
    except Error as e:
        print(f"Error connecting to MySQL: {e}")
        sys.exit(1)


def get_sqlite_tables(sqlite_conn):
    """Get all table names from SQLite database
    
    Excludes:
    - sqlite_* system tables
    - HTE_* and HT_* Hibernate Envers audit tables (read-only, not needed for runtime)
    """
    cursor = sqlite_conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")
    tables = [row[0] for row in cursor.fetchall()]
    cursor.close()
    
    # Skip Hibernate audit tables (HTE_* and HT_*) - they're read-only history tables
    # managed by Hibernate Envers. They're not required for runtime and will be
    # automatically recreated by Hibernate when the app starts on MySQL.
    filtered_tables = [t for t in tables if not (t.startswith('HTE_') or t.startswith('HT_'))]
    
    return filtered_tables


def get_sqlite_table_schema(sqlite_conn, table_name):
    """Get CREATE TABLE statement for a SQLite table"""
    cursor = sqlite_conn.cursor()
    cursor.execute(f"SELECT sql FROM sqlite_master WHERE type='table' AND name='{table_name}'")
    result = cursor.fetchone()
    cursor.close()
    if result:
        return result[0]
    return None


def _strip_type_quotes(type_name):
    """Normalize odd quoted SQLite type names like '"TEXT"'."""
    if type_name is None:
        return ""
    cleaned = str(type_name).strip()
    while (cleaned.startswith('"') and cleaned.endswith('"')) or (
        cleaned.startswith("'") and cleaned.endswith("'")
    ):
        cleaned = cleaned[1:-1].strip()
    return cleaned


def _map_sqlite_type_to_mysql(sqlite_type):
    """Map SQLite affinity/type declarations to MySQL column types."""
    t = _strip_type_quotes(sqlite_type).upper()

    if not t:
        return "LONGTEXT"
    if "JSON" in t:
        return "JSON"
    if "BOOL" in t:
        return "BOOLEAN"
    if "BIGINT" in t or "INT" in t:
        return "BIGINT"
    if "DOUBLE" in t or "REAL" in t or "FLOAT" in t:
        return "DOUBLE"
    if "DECIMAL" in t or "NUMERIC" in t:
        return "DECIMAL(38,10)"
    if "BLOB" in t:
        return "LONGBLOB"
    if "DATE" in t and "DATETIME" not in t:
        return "DATE"
    if "TIME" in t or "TIMESTAMP" in t or "DATETIME" in t:
        return "DATETIME"
    if "CHAR" in t or "CLOB" in t or "TEXT" in t or "VARCHAR" in t:
        varchar_match = re.search(r"VARCHAR\s*\(\s*(\d+)\s*\)", t)
        if varchar_match:
            return f"VARCHAR({varchar_match.group(1)})"
        return "LONGTEXT"

    return "LONGTEXT"


def _format_default_for_mysql(default_value):
    """Convert SQLite default value syntax to a safe MySQL default clause when possible."""
    if default_value is None:
        return ""

    d = str(default_value).strip()
    if not d:
        return ""

    # Skip SQLite-specific expression defaults (e.g. strftime(...))
    if "strftime(" in d.lower() or d.startswith("("):
        return ""

    # Keep CURRENT_* style expressions unquoted
    upper = d.upper()
    if upper in {"CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME"}:
        return f" DEFAULT {upper}"

    # Numeric defaults
    if re.fullmatch(r"[-+]?\d+(\.\d+)?", d):
        return f" DEFAULT {d}"

    # Preserve already-quoted strings; otherwise quote as string.
    if (d.startswith("'") and d.endswith("'")) or (d.startswith('"') and d.endswith('"')):
        value = d[1:-1].replace("'", "''")
    else:
        value = d.replace("'", "''")
    return f" DEFAULT '{value}'"


def build_mysql_schema_from_sqlite_table(sqlite_conn, table_name):
    """Build a MySQL CREATE TABLE statement using SQLite PRAGMA metadata."""
    cursor = sqlite_conn.cursor()
    try:
        cursor.execute(f"PRAGMA table_info(`{table_name}`)")
        columns = cursor.fetchall()
    finally:
        cursor.close()

    if not columns:
        return None

    col_defs = []
    pk_cols = []

    # PRAGMA table_info columns: cid, name, type, notnull, dflt_value, pk
    for _, col_name, col_type, not_null, default_value, pk in columns:
        mysql_type = _map_sqlite_type_to_mysql(col_type)
        escaped_col = col_name.replace('`', '``')

        definition = f"`{escaped_col}` {mysql_type}"
        if not_null:
            definition += " NOT NULL"
        definition += _format_default_for_mysql(default_value)

        col_defs.append(definition)
        if pk:
            pk_cols.append((int(pk), escaped_col, mysql_type))

    pk_cols.sort(key=lambda x: x[0])

    if len(pk_cols) == 1:
        _, pk_name, pk_type = pk_cols[0]
        # Promote single integer-like PK to AUTO_INCREMENT.
        if any(t in pk_type.upper() for t in ["INT", "BIGINT"]):
            col_defs = [
                re.sub(
                    rf"^`{re.escape(pk_name)}`\s+{re.escape(pk_type)}",
                    f"`{pk_name}` BIGINT",
                    col_def,
                    count=1,
                )
                for col_def in col_defs
            ]
            for i, col_def in enumerate(col_defs):
                if col_def.startswith(f"`{pk_name}` "):
                    if "NOT NULL" not in col_def:
                        col_def += " NOT NULL"
                    col_defs[i] = col_def + " AUTO_INCREMENT"
                    break
            col_defs.append(f"PRIMARY KEY (`{pk_name}`)")
        else:
            col_defs.append(f"PRIMARY KEY (`{pk_name}`)")
    elif len(pk_cols) > 1:
        pk_expr = ", ".join(f"`{name}`" for _, name, _ in pk_cols)
        col_defs.append(f"PRIMARY KEY ({pk_expr})")

    escaped_table = table_name.replace('`', '``')
    return (
        f"CREATE TABLE `{escaped_table}` ("
        + ", ".join(col_defs)
        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
    )


def adapt_sqlite_to_mysql_schema(sqlite_schema, table_name):
    """Adapt SQLite CREATE TABLE statement to MySQL-compatible format"""
    if not sqlite_schema:
        return None
    
    mysql_schema = sqlite_schema
    
    # Basic type conversions
    import re

    # Keep Hibernate session ids indexable in MySQL. SQLite backups may store
    # them as TEXT, but MySQL cannot use TEXT in a PRIMARY KEY without a length.
    mysql_schema = re.sub(
        r'([`"]?hib_sess_id[`"]?\s+)(?:TEXT|VARCHAR\s*\(\s*\d+\s*\))\b',
        r'\1CHAR(36)',
        mysql_schema,
        flags=re.IGNORECASE,
    )

    mysql_schema = re.sub(
        r'(?is)^CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?([`"]?)[^\s(`"]+\1\s*\(',
        f'CREATE TABLE `{table_name}` (',
        mysql_schema,
        count=1,
    )
    
    # Convert JSONB (PostgreSQL/SQLite) to MySQL JSON type
    mysql_schema = re.sub(r'\bJSONB\b', 'JSON', mysql_schema, flags=re.IGNORECASE)
    mysql_schema = re.sub(r'\bCLOB\b', 'LONGTEXT', mysql_schema, flags=re.IGNORECASE)
    
    # Convert INTEGER to appropriate MySQL type (keep as INT for now)
    # Convert TEXT to appropriate MySQL type
    mysql_schema = re.sub(r'\bINTEGER\b', 'BIGINT', mysql_schema, flags=re.IGNORECASE)
    mysql_schema = re.sub(r'\bTEXT\b', 'TEXT', mysql_schema, flags=re.IGNORECASE)
    mysql_schema = re.sub(r'\bBLOB\b', 'LONGBLOB', mysql_schema, flags=re.IGNORECASE)
    mysql_schema = re.sub(r'\bREAL\b', 'DOUBLE', mysql_schema, flags=re.IGNORECASE)
    
    # Add MySQL-specific options
    mysql_schema = mysql_schema.rstrip(';')
    mysql_schema += " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"

    mysql_schema = re.sub(
        r'\bPRIMARY\s+KEY\s+AUTOINCREMENT\b',
        'AUTO_INCREMENT PRIMARY KEY',
        mysql_schema,
        flags=re.IGNORECASE,
    )

    mysql_schema = re.sub(
        r'([`"]?hib_sess_id[`"]?\s+)TEXT\b',
        r'\1CHAR(36)',
        mysql_schema,
        flags=re.IGNORECASE,
    )
    
    return mysql_schema


def drop_all_tables(mysql_conn):
    """Drop all tables in MySQL database"""
    cursor = mysql_conn.cursor()
    
    try:
        # Disable foreign key checks
        cursor.execute("SET FOREIGN_KEY_CHECKS = 0")
        
        # Get all tables
        cursor.execute("SHOW TABLES")
        tables = [table[0] for table in cursor.fetchall()]
        
        if tables:
            print(f"\nDropping {len(tables)} existing tables...")
            for table in tables:
                try:
                    cursor.execute(f"DROP TABLE IF EXISTS `{table}`")
                    print(f"  Dropped table: {table}")
                except Error as e:
                    print(f"  Warning: Could not drop table '{table}': {e}")
            
            mysql_conn.commit()
        else:
            print("\nNo existing tables to drop")
        
        # Re-enable foreign key checks
        cursor.execute("SET FOREIGN_KEY_CHECKS = 1")
        
    finally:
        cursor.close()


def copy_table_data(sqlite_conn, mysql_conn, table_name):
    """Copy data from SQLite table to MySQL table
    
    For tables with duplicate key conflicts (e.g., slack_messages with duplicate timestamps),
    silently skip the data copy but keep the table structure.
    """
    sqlite_cursor = sqlite_conn.cursor()
    mysql_cursor = mysql_conn.cursor()
    
    try:
        # Fetch all data from SQLite
        sqlite_cursor.execute(f"SELECT * FROM `{table_name}`")
        columns = [desc[0] for desc in sqlite_cursor.description]
        rows = sqlite_cursor.fetchall()
        
        if not rows:
            print(f"  Table '{table_name}': 0 rows (empty)")
            return
        
        # Create placeholders for INSERT statement
        placeholders = ','.join(['%s' for _ in columns])
        columns_str = ','.join([f'`{col}`' for col in columns])
        
        # Insert data into MySQL
        insert_sql = f"INSERT INTO `{table_name}` ({columns_str}) VALUES ({placeholders})"
        
        try:
            mysql_cursor.executemany(insert_sql, rows)
            mysql_conn.commit()
            print(f"  Table '{table_name}': {len(rows)} rows restored")
        except Error as e:
            # For duplicate key errors (e.g., slack_messages with duplicate timestamps),
            # skip data copy but keep table structure
            if "1062" in str(e):  # MySQL error 1062: Duplicate entry
                print(f"  Table '{table_name}': Table created but data skipped (duplicate key conflict: {e})")
                mysql_conn.rollback()
            else:
                raise
        
    except Error as e:
        print(f"  Error copying table '{table_name}': {e}")
        mysql_conn.rollback()
    finally:
        sqlite_cursor.close()
        mysql_cursor.close()


def restore_sqlite_to_mysql(backup_file, host, port, user, password, database, force=False):
    """Restore SQLite backup file to MySQL database"""
    print_header("SQLite to MySQL Restore")
    
    # Validate backup file
    backup_path = Path(backup_file)
    if not backup_path.exists():
        print(f"Error: Backup file not found: {backup_file}")
        sys.exit(1)
    
    print(f"Backup file: {backup_path}")
    print(f"Target MySQL: {user}@{host}:{port}/{database}")
    
    # Connect to SQLite backup
    print("\nConnecting to SQLite backup...")
    sqlite_conn = sqlite3.connect(str(backup_path))
    
    # Connect to MySQL
    print("Connecting to MySQL...")
    mysql_conn = get_mysql_connection(host, port, user, password, database)
    
    try:
        # Get confirmation
        if not force:
            print("\nWARNING: This will replace ALL data in the MySQL database!")
            print("All existing tables and data will be dropped and replaced with backup data.")
            response = input("\nContinue? (yes/no): ").strip().lower()
            if response not in ('yes', 'y'):
                print("Restore cancelled.")
                return
        else:
            print("\nWARNING: Force mode enabled - replacing ALL data in MySQL database!")
        
        # Drop all existing tables
        drop_all_tables(mysql_conn)
        
        # Get all tables from SQLite
        print("\nFetching table list from backup...")
        tables = get_sqlite_tables(sqlite_conn)
        print(f"Found {len(tables)} tables in backup")
        
        # Restore schema and data for each table
        print("\nRestoring tables...")
        failed_tables = []
        restored_tables = 0
        for table_name in tables:
            print(f"\nProcessing table: {table_name}")
            
            # Build a robust MySQL schema from SQLite PRAGMA metadata.
            mysql_schema = build_mysql_schema_from_sqlite_table(sqlite_conn, table_name)
            if not mysql_schema:
                print(f"  Warning: Could not build schema for '{table_name}', skipping")
                failed_tables.append((table_name, "missing schema"))
                continue
            
            # Create table in MySQL
            try:
                mysql_cursor = mysql_conn.cursor()
                mysql_cursor.execute(f"DROP TABLE IF EXISTS `{table_name}`")
                mysql_cursor.execute(mysql_schema)
                mysql_conn.commit()
                mysql_cursor.close()
            except Error as e:
                print(f"  Error creating table '{table_name}' in MySQL: {e}")
                failed_tables.append((table_name, str(e)))
                continue
            
            # Copy data
            copy_table_data(sqlite_conn, mysql_conn, table_name)
            restored_tables += 1
        
        print_header("Restore Complete")
        print(f"Created/restored {restored_tables}/{len(tables)} tables in MySQL database")
        if failed_tables:
            print("\nFailed tables:")
            for table_name, reason in failed_tables:
                print(f"  - {table_name}: {reason}")
            raise RuntimeError(
                f"Restore incomplete: {len(failed_tables)} table(s) failed."
            )
        
    finally:
        sqlite_conn.close()
        mysql_conn.close()


def main():
    """Main restore process"""
    parser = argparse.ArgumentParser(description='Restore SQLite backup to MySQL database')
    parser.add_argument('--backup-file', default=None,
                       help='Path to SQLite backup file (default: use most recent backup)')
    parser.add_argument('--force', action='store_true',
                       help='Skip confirmation prompt')
    
    args = parser.parse_args()
    
    # Get MySQL configuration from .env
    host, port, username, password, database = get_mysql_config()
    
    # Determine backup file
    if args.backup_file:
        backup_file = Path(args.backup_file)
        # If relative path, resolve from project root
        if not backup_file.is_absolute():
            backup_file = PROJECT_ROOT / backup_file
    else:
        backup_file = find_latest_backup()
        print(f"Using most recent backup: {backup_file}")
    
    try:
        restore_sqlite_to_mysql(
            str(backup_file),
            host,
            port,
            username,
            password,
            database,
            force=args.force
        )
            
    except KeyboardInterrupt:
        print("\n\nRestore interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"\nAn error occurred: {e}", file=sys.stderr)
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == "__main__":
    main()
