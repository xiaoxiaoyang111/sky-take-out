[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$rentalRoot=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'Get-IdeaRedisPassword.ps1')
$localPath=Join-Path $rentalRoot '.rental/application-local.yml'
$localText=if(Test-Path $localPath){[IO.File]::ReadAllText($localPath)}else{''}
function Read-VerificationSetting([string]$EnvName,[string]$Key,[string]$Fallback){
 $value=[Environment]::GetEnvironmentVariable($EnvName)
 if($value){return $value}
 $found=[regex]::Match($localText,'(?m)^\s+'+[regex]::Escape($Key)+':\s*(.+?)\s*$')
 if($found.Success){return $found.Groups[1].Value.Trim().Trim([char]34).Trim([char]39)}
 return $Fallback
}
$dbHost=Read-VerificationSetting SKY_DB_HOST host localhost
$dbPort=Read-VerificationSetting SKY_DB_PORT port 3306
$dbUser=Read-VerificationSetting SKY_DB_USERNAME username root
$dbPassword=Read-VerificationSetting SKY_DB_PASSWORD password ''
$mysql=if($env:MYSQL_CLI){$env:MYSQL_CLI}elseif(Test-Path 'C:/mysql/mysql-8.0.46-winx64/bin/mysql.exe'){'C:/mysql/mysql-8.0.46-winx64/bin/mysql.exe'}else{'mysql'}
$maven=Get-Command mvn.cmd,mvn -ErrorAction SilentlyContinue|Select-Object -First 1
$mavenPath=if($maven){$maven.Source}else{(Get-ChildItem "$env:ProgramFiles/JetBrains/IntelliJ IDEA */plugins/maven/lib/maven3/bin/mvn.cmd"|Select-Object -First 1).FullName}
$database='rental_verify_'+[guid]::NewGuid().ToString('N').Substring(0,12)
if($database -notmatch '^rental_verify_[a-f0-9]{12}$'){throw 'Unsafe verification database name'}
$names=@('MYSQL_PWD','SKY_DB_HOST','SKY_DB_PORT','SKY_DB_DATABASE','SKY_DB_USERNAME','SKY_DB_PASSWORD','SKY_DB_INTEGRATION','SKY_REDIS_INTEGRATION','SKY_REDIS_PASSWORD','SKY_DB_URL','RENTAL_JWT_SECRET','SKY_ADMIN_JWT_SECRET','SKY_UPLOAD_PATH','RENTAL_BOOTSTRAP_USERNAME','RENTAL_BOOTSTRAP_PASSWORD')
$saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name)}
$created=$false;$passed=$false
try{
 $env:MYSQL_PWD=$dbPassword
 & $mysql --host=$dbHost --port=$dbPort --user=$dbUser --execute="CREATE DATABASE $database CHARACTER SET utf8mb4;"
 if($LASTEXITCODE -ne 0){throw 'Could not create isolated verification database'};$created=$true
 foreach($file in 'database/rental/000-employee.sql','database/rental/001-schema.sql','database/rental/001b-order-index.sql','database/rental/003-member-center.sql','database/rental/004-customer-avatar.sql','database/testing/legacy-schema.sql'){
  $sql=[IO.File]::ReadAllText((Join-Path $rentalRoot $file)); & $mysql --host=$dbHost --port=$dbPort --user=$dbUser --database=$database --default-character-set=utf8mb4 --execute=$sql
  if($LASTEXITCODE -ne 0){throw 'Verification schema initialization failed'}
 }
 $env:SKY_DB_HOST=$dbHost;$env:SKY_DB_PORT=$dbPort;$env:SKY_DB_DATABASE=$database;$env:SKY_DB_USERNAME=$dbUser;$env:SKY_DB_PASSWORD=$dbPassword
 $env:SKY_DB_INTEGRATION='true';$env:SKY_REDIS_INTEGRATION='true'
 if(!$env:SKY_REDIS_PASSWORD){$env:SKY_REDIS_PASSWORD=Get-IdeaRedisPassword -Path (Join-Path $rentalRoot '.idea/workspace.xml')}
 Push-Location $rentalRoot
 try{& $mavenPath -q '-Dmaven.compiler.release=8' package *> (Join-Path $rentalRoot 'sky-server/target/resume-fresh-database.log');$passed=($LASTEXITCODE -eq 0)}finally{Pop-Location}
 if(!$passed){throw 'Fresh database verification failed; inspect sky-server/target/resume-fresh-database.log'}
 $env:SKY_DB_URL="jdbc:mysql://${dbHost}:${dbPort}/${database}?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
 $env:RENTAL_JWT_SECRET=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
 $env:SKY_ADMIN_JWT_SECRET=[guid]::NewGuid().ToString('N')+[guid]::NewGuid().ToString('N')
 $env:SKY_UPLOAD_PATH=Join-Path $rentalRoot 'sky-server/target/prod-verification-uploads'
 $env:RENTAL_BOOTSTRAP_USERNAME='fresh_verify_admin'
 $env:RENTAL_BOOTSTRAP_PASSWORD=[guid]::NewGuid().ToString('N')
 $jar=Join-Path $rentalRoot 'sky-server/target/sky-server-1.0-SNAPSHOT.jar'
 $out=Join-Path $rentalRoot 'sky-server/target/resume-prod-bootstrap.log';$err=Join-Path $rentalRoot 'sky-server/target/resume-prod-bootstrap-error.log'
 $process=Start-Process -FilePath java -ArgumentList @('-Dfile.encoding=UTF-8','-jar',('"'+$jar+'"'),'--spring.profiles.active=prod,bootstrap','--server.port=0') -WorkingDirectory $rentalRoot -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
 if(!$process.WaitForExit(30000)){Stop-Process -Id $process.Id;throw 'Production bootstrap did not exit after provisioning'}
 if($process.ExitCode -ne 0){throw 'Production bootstrap validation failed; inspect bootstrap logs'}
 $count=& $mysql --host=$dbHost --port=$dbPort --user=$dbUser --database=$database --batch --skip-column-names --execute="select count(*) from employee where username='fresh_verify_admin' and left(password,2)=concat(char(36),'2');"
 if($LASTEXITCODE -ne 0 -or $count -ne '1'){throw 'Provisioned administrator was not persisted with BCrypt'}
 Write-Output 'PASS: production profile and one-off BCrypt administrator provisioning'
 Write-Output 'PASS: fresh database migrations and complete Java 8 compatible package'
}finally{
 if($created){
  # Only this randomly named database created above is eligible for cleanup.
  if($database -notmatch '^rental_verify_[a-f0-9]{12}$'){throw 'Refusing unsafe database cleanup'}
  & $mysql --host=$dbHost --port=$dbPort --user=$dbUser --execute="DROP DATABASE $database;"
  if($LASTEXITCODE -ne 0){Write-Warning 'Owned verification database cleanup failed'}else{Write-Output 'Isolated verification database removed'}
 }
 foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
